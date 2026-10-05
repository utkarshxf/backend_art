package com.basic.JWTSecurity.call.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.service.ChatException;
import com.basic.JWTSecurity.chat.service.DevicePusher;
import com.basic.JWTSecurity.chat.service.FirestoreRestClient;
import com.basic.JWTSecurity.chat.service.FirestoreRestClient.Snapshot;
import com.basic.JWTSecurity.chat.service.FirestoreRestClient.Write;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 1:1 audio / video calls between two chat users. The media runs on Agora: one channel per call, named after the
 * call id, which the caller joins as uid 1 and the callee as uid 2, each with a token minted here. This service owns
 * the call's lifecycle: it is the only writer of {@code calls/{callId}} in Firestore (both phones listen to it), it
 * wakes the callee's phone with an FCM push, and when a call is over it writes the call's row into the chat thread.
 * <pre>
 *   ringing -> accepted -> ended
 *   ringing -> declined | busy        (the callee)
 *   ringing -> cancelled | missed     (the caller gave up | nobody answered in time)
 * </pre>
 * Every step reads the call, decides, and commits with the version it read as a precondition, so two phones acting
 * at the same moment (answer vs. hang up) cannot both win.
 */
@Service
public class CallService {

    public static final String RINGING = "ringing";
    public static final String ACCEPTED = "accepted";
    public static final String ENDED = "ended";
    public static final String DECLINED = "declined";
    public static final String CANCELLED = "cancelled";
    public static final String MISSED = "missed";
    public static final String BUSY = "busy";

    static final String AUDIO = "audio";
    static final String VIDEO = "video";

    /** How long the callee's phone rings; the caller's app gives up (cancel with timeout) after this. */
    static final int RING_TIMEOUT_SEC = 45;
    /** After the ring time plus this margin (a slow push, a caller app that died) an unanswered call is missed. */
    static final Duration RING_EXPIRY = Duration.ofSeconds(RING_TIMEOUT_SEC + 15);
    static final Duration TOKEN_VALIDITY = Duration.ofHours(1);
    static final int CALLER_UID = 1;
    static final int CALLEE_UID = 2;
    /** "Stop ringing" for the callee's other devices is useless a minute later. */
    static final Duration ENDED_PUSH_TTL = Duration.ofSeconds(60);
    /** A phone that was offline while it was called still shows the missed call when it comes back within a day. */
    static final Duration MISSED_PUSH_TTL = Duration.ofDays(1);
    static final int MAX_STARTS_PER_MINUTE = 6;

    static final String PUSH_INCOMING = "incoming_call";
    static final String PUSH_ENDED = "call_ended";
    static final String REASON_CANCELLED = "cancelled";
    static final String REASON_MISSED = "missed";
    static final String REASON_ANSWERED = "answered";
    static final String REASON_DECLINED = "declined";

    private static final Set<String> STATUSES = Set.of(RINGING, ACCEPTED, ENDED, DECLINED, CANCELLED, MISSED, BUSY);
    /** The callee never saw these calls through: they count as unread in the inbox. */
    private static final Set<String> UNSEEN = Set.of(MISSED, CANCELLED, BUSY);
    private static final Pattern CALL_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** Usernames are at most this long in the chat rules. */
    private static final int MAX_USERNAME = 100;
    /** Reading and committing again after losing a race; a call has two parties, so one retry is the normal worst case. */
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_REMEMBERED = 10_000;

    private static final Logger logger = LoggerFactory.getLogger(CallService.class);

    /** Runs a task once after a delay; tests run the tasks by hand. */
    interface Timer {
        void schedule(Runnable task, Duration delay);
    }

    /** {@code calls/{callId}} as read from Firestore, with the version it was read at. */
    record Call(String id, String caller, String callee, String conversationId, String kind, String status,
                Instant createdAt, Instant answeredAt, long durationSec, String updateTime) {

        /** This call after a write. Its new version is unknown: every step reads the call again before writing. */
        Call as(String status, Instant answeredAt, long durationSec) {
            return new Call(id, caller, callee, conversationId, kind, status, createdAt, answeredAt, durationSec, "");
        }
    }

    private record Window(long minute, int count) {
    }

    private final AgoraTokens agora;
    private final ChatFirebaseCredentials credentials;
    private final FirestoreRestClient firestore;
    private final DevicePusher pusher;
    private final Clock clock;
    private final Timer timer;
    /** Only set when this service runs its own timer thread. */
    private final ScheduledExecutorService executor;

    /** caller -> the call of theirs that is ringing: a caller rings one phone at a time. */
    private final Map<String, String> ringingByCaller = new ConcurrentHashMap<>();
    /** caller -> calls started in the current minute. */
    private final Map<String, Window> starts = new ConcurrentHashMap<>();
    private final Object[] startLocks = new Object[64];

    @Autowired
    public CallService(AgoraTokens agora, ChatFirebaseCredentials credentials, FirestoreRestClient firestore,
                       DevicePusher pusher) {
        this(agora, credentials, firestore, pusher, Clock.systemUTC(), null);
    }

    CallService(AgoraTokens agora, ChatFirebaseCredentials credentials, FirestoreRestClient firestore,
                DevicePusher pusher, Clock clock, Timer timer) {
        this.agora = agora;
        this.credentials = credentials;
        this.firestore = firestore;
        this.pusher = pusher;
        this.clock = clock;
        if (timer != null) {
            this.executor = null;
            this.timer = timer;
        } else {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "call-timer");
                thread.setDaemon(true);
                return thread;
            });
            this.executor = scheduler;
            this.timer = (task, delay) -> scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
        for (int i = 0; i < startLocks.length; i++) {
            startLocks[i] = new Object();
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Calls need the Agora keys (tokens) and the Firebase service account (call state and pushes). */
    public boolean isConfigured() {
        return agora.isConfigured() && credentials.isConfigured();
    }

    public CallConfig config() {
        boolean enabled = isConfigured();
        return new CallConfig(enabled, enabled ? agora.appId() : null, RING_TIMEOUT_SEC);
    }

    /**
     * {@code caller} calls {@code callee}: creates the call, rings the callee's devices and answers what the caller's
     * app needs to join the channel. A repeated request while that same call is still ringing gets the same call
     * back instead of ringing twice; a ring to someone else is cancelled first.
     */
    public CallSession start(String caller, String calleeName, String kindName) {
        requireConfigured();
        String callee = calleeName == null ? "" : calleeName.trim();
        String kind = kindName == null ? "" : kindName.trim();
        if (!AUDIO.equals(kind) && !VIDEO.equals(kind)) {
            throw ChatException.badRequest("Unknown call type");
        }
        if (!isUsername(callee)) {
            throw ChatException.badRequest("Choose who to call");
        }
        if (!isUsername(caller)) {
            throw ChatException.badRequest("This account cannot make calls");
        }
        if (callee.equals(caller)) {
            throw ChatException.badRequest("You can't call yourself");
        }

        synchronized (startLocks[Math.floorMod(caller.hashCode(), startLocks.length)]) {
            Instant now = now();
            if (overLimit(caller, now)) {
                throw new ChatException(HttpStatus.TOO_MANY_REQUESTS, "You're calling too often. Try again in a minute.");
            }
            Optional<Call> repeated = settlePreviousRing(caller, callee, kind, now);
            if (repeated.isPresent()) {
                return joinable(repeated.get(), caller, true);
            }
            // only people who have opened chat have a profile there, and only they can be rung
            if (firestore.getDocument("users", callee).isEmpty()) {
                throw ChatException.notFound("This person can't be called yet");
            }

            Call call = new Call(UUID.randomUUID().toString().replace("-", ""), caller, callee,
                    conversationId(caller, callee), kind, RINGING, now, null, 0, "");
            // minted first: if the token cannot be built nobody is rung
            CallSession session = joinable(call, caller, true);

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("usernames", sorted(caller, callee));
            document.put("conversationId", call.conversationId());
            document.put("caller", caller);
            document.put("callee", callee);
            document.put("kind", kind);
            document.put("status", RINGING);
            document.put("createdAt", now);
            document.put("answeredAt", null);
            document.put("endedAt", null);
            document.put("endedBy", null);
            document.put("durationSec", 0L);
            if (!firestore.commit(List.of(firestore.create(document, "calls", call.id())))) {
                // a random id that is already taken: practically impossible
                throw ChatException.upstream();
            }
            ringingByCaller.put(caller, call.id());
            timer.schedule(() -> expire(call.id()), RING_EXPIRY);
            ring(call);
            return session;
        }
    }

    /** The callee answers: ringing -> accepted. Answers what the callee's app needs to join the channel. */
    public CallSession accept(String user, String callId) {
        requireConfigured();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Call call = require(callId, user);
            if (!user.equals(call.callee())) {
                throw ChatException.forbidden("Only the person being called can answer");
            }
            if (ACCEPTED.equals(call.status())) {
                // a repeated request: the same answer with a fresh token
                return joinable(call, user, false);
            }
            if (!RINGING.equals(call.status())) {
                throw CallException.conflict("This call has ended", call.status());
            }
            Instant now = now();
            if (!now.isBefore(call.createdAt().plus(RING_EXPIRY))) {
                Optional<Call> missed = finish(call, MISSED, null, now);
                if (missed.isEmpty()) {
                    continue;
                }
                pushEnded(missed.get(), REASON_MISSED);
                throw CallException.conflict("This call has ended", MISSED);
            }
            Map<String, Object> answered = new LinkedHashMap<>();
            answered.put("status", ACCEPTED);
            answered.put("answeredAt", now);
            Write write = firestore.update(answered, "calls", call.id()).ifUnchangedSince(call.updateTime());
            if (!firestore.commit(List.of(write))) {
                continue;
            }
            ringingByCaller.remove(call.caller(), call.id());
            Call accepted = call.as(ACCEPTED, now, 0);
            logger.info("Call {} answered", call.id());
            // the callee's other devices stop ringing; not worth keeping the answering phone waiting for
            timer.schedule(() -> pushEnded(accepted, REASON_ANSWERED), Duration.ZERO);
            return joinable(accepted, user, false);
        }
        throw ChatException.upstream();
    }

    /** The callee declines, or their phone is on another call ({@code busy}): ringing -> declined | busy. */
    public CallSession decline(String user, String callId, boolean busy) {
        requireConfigured();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Call call = require(callId, user);
            if (!user.equals(call.callee())) {
                throw ChatException.forbidden("Only the person being called can decline");
            }
            if (ACCEPTED.equals(call.status())) {
                // answered on another device: declining here must not hang that call up
                throw CallException.conflict("This call was answered on another device", ACCEPTED);
            }
            if (!RINGING.equals(call.status())) {
                return summary(call);
            }
            Optional<Call> over = finish(call, busy ? BUSY : DECLINED, user, now());
            if (over.isEmpty()) {
                continue;
            }
            // the callee's other devices stop ringing; a busy phone never showed the call, so there it was missed
            pushEnded(over.get(), busy ? REASON_MISSED : REASON_DECLINED);
            return summary(over.get());
        }
        throw ChatException.upstream();
    }

    /** The caller hangs up before an answer, or their ring time ran out ({@code timeout}): ringing -> cancelled | missed. */
    public CallSession cancel(String user, String callId, boolean timeout) {
        requireConfigured();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Call call = require(callId, user);
            if (!user.equals(call.caller())) {
                throw ChatException.forbidden("Only the caller can cancel");
            }
            Optional<Call> over;
            if (RINGING.equals(call.status())) {
                over = finish(call, timeout ? MISSED : CANCELLED, user, now());
                over.ifPresent(done -> pushEnded(done, timeout ? REASON_MISSED : REASON_CANCELLED));
            } else if (ACCEPTED.equals(call.status())) {
                // answered at the very moment the caller gave up: the caller is leaving, so the call is over
                over = finish(call, ENDED, user, now());
            } else {
                return summary(call);
            }
            if (over.isPresent()) {
                return summary(over.get());
            }
        }
        throw ChatException.upstream();
    }

    /** Either side hangs up: accepted -> ended. On a call that is still ringing it cancels (caller) or declines (callee). */
    public CallSession end(String user, String callId) {
        requireConfigured();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Call call = require(callId, user);
            Optional<Call> over;
            if (ACCEPTED.equals(call.status())) {
                over = finish(call, ENDED, user, now());
            } else if (RINGING.equals(call.status())) {
                boolean caller = user.equals(call.caller());
                over = finish(call, caller ? CANCELLED : DECLINED, user, now());
                over.ifPresent(done -> pushEnded(done, caller ? REASON_CANCELLED : REASON_DECLINED));
            } else {
                return summary(call);
            }
            if (over.isPresent()) {
                return summary(over.get());
            }
        }
        throw ChatException.upstream();
    }

    /** A fresh token for a call in progress (tokens last an hour), or for the caller while it rings. */
    public CallSession token(String user, String callId) {
        requireConfigured();
        Call call = require(callId, user);
        if (ACCEPTED.equals(call.status()) || (RINGING.equals(call.status()) && user.equals(call.caller()))) {
            return joinable(call, user, false);
        }
        throw CallException.conflict(RINGING.equals(call.status()) ? "Answer the call first" : "This call has ended",
                call.status());
    }

    /** Runs when a call's ring time is over: a call nobody answered, declined or cancelled by then was missed. */
    void expire(String callId) {
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                Optional<Call> call = load(callId);
                if (call.isEmpty()) {
                    return;
                }
                ringingByCaller.remove(call.get().caller(), callId);
                if (!RINGING.equals(call.get().status())) {
                    return;
                }
                Optional<Call> missed = finish(call.get(), MISSED, null, now());
                if (missed.isPresent()) {
                    pushEnded(missed.get(), REASON_MISSED);
                    return;
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Could not expire call {}: {}", callId, e.toString());
        }
    }

    // ------------------------------------------------------------------ steps

    /**
     * What to do about the call {@code caller} is already ringing someone with. The same person and kind, still
     * ringing: that call (the app retried its request). Anything else is over before the new call starts.
     */
    private Optional<Call> settlePreviousRing(String caller, String callee, String kind, Instant now) {
        String previousId = ringingByCaller.get(caller);
        if (previousId == null) {
            return Optional.empty();
        }
        Optional<Call> previous = load(previousId).filter(call -> RINGING.equals(call.status()));
        if (previous.isPresent()) {
            Call call = previous.get();
            boolean rangOut = !now.isBefore(call.createdAt().plus(RING_EXPIRY));
            if (!rangOut && call.callee().equals(callee) && call.kind().equals(kind)) {
                return previous;
            }
            // losing this race means the call was answered or ended meanwhile, which settles it just as well
            finish(call, rangOut ? MISSED : CANCELLED, caller, now)
                    .ifPresent(done -> pushEnded(done, rangOut ? REASON_MISSED : REASON_CANCELLED));
        }
        ringingByCaller.remove(caller, previousId);
        return Optional.empty();
    }

    /**
     * Ends {@code call} with the final {@code status} and, in the same commit, writes its row into the chat thread
     * and the inbox preview. Empty when the call (or the conversation) changed since it was read: nothing was
     * written and the caller of this method reads again.
     */
    private Optional<Call> finish(Call call, String status, String endedBy, Instant now) {
        long duration = ENDED.equals(status) && call.answeredAt() != null
                ? Math.max(0, Duration.between(call.answeredAt(), now).getSeconds()) : 0;
        String preview = preview(status, call.kind());
        boolean unseen = UNSEEN.contains(status);

        Map<String, Object> over = new LinkedHashMap<>();
        over.put("status", status);
        over.put("endedAt", now);
        over.put("endedBy", endedBy);
        over.put("durationSec", duration);

        // the thread row; the app draws it from `call` (older versions show `text`). Its id is the call id.
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("id", call.id());
        log.put("kind", call.kind());
        log.put("outcome", ENDED.equals(status) ? "completed" : status);
        log.put("durationSec", duration);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("sender", call.caller());
        message.put("type", "call");
        message.put("text", preview);
        message.put("call", log);
        message.put("reactions", Map.of());
        message.put("unsent", false);
        message.put("createdAt", now);

        Map<String, Object> last = new LinkedHashMap<>();
        last.put("id", call.id());
        last.put("sender", call.caller());
        last.put("type", "call");
        last.put("preview", preview);
        last.put("createdAt", now);

        List<Write> writes = new ArrayList<>();
        writes.add(firestore.update(over, "calls", call.id()).ifUnchangedSince(call.updateTime()));
        writes.add(firestore.create(message, "conversations", call.conversationId(), "messages", call.id()));
        if (firestore.getSnapshot("conversations", call.conversationId()).isPresent()) {
            Map<String, Object> inbox = new LinkedHashMap<>();
            inbox.put("lastMessage", last);
            inbox.put("updatedAt", now);
            Write update = firestore.update(inbox, "conversations", call.conversationId());
            if (unseen) {
                update.increment(FirestoreRestClient.fieldPath("unread", call.callee()), 1);
            }
            writes.add(update);
        } else {
            // these two never chatted: the document the app creates with a first message
            Map<String, Object> conversation = new LinkedHashMap<>();
            conversation.put("usernames", sorted(call.caller(), call.callee()));
            conversation.put("createdAt", now);
            conversation.put("updatedAt", now);
            conversation.put("lastMessage", last);
            conversation.put("unread", unseen ? Map.of(call.callee(), 1L) : Map.of());
            conversation.put("lastRead", Map.of());
            conversation.put("typing", Map.of());
            conversation.put("muted", Map.of());
            conversation.put("markedUnread", Map.of());
            conversation.put("clearedAt", Map.of());
            writes.add(firestore.create(conversation, "conversations", call.conversationId()));
        }
        if (!firestore.commit(writes)) {
            return Optional.empty();
        }
        ringingByCaller.remove(call.caller(), call.id());
        logger.info("Call {} is over: {} ({} s)", call.id(), status, duration);
        return Optional.of(call.as(status, call.answeredAt(), duration));
    }

    /** Wakes the callee's devices. Best effort: without a push the call simply rings out. */
    private void ring(Call call) {
        try {
            if (mutedByCallee(call)) {
                // muting a chat is the only way to keep someone quiet, so it silences their calls too
                logger.info("Call {} does not ring: the callee muted this chat", call.id());
                return;
            }
            List<String> tokens = pusher.tokens(call.callee());
            if (tokens.isEmpty()) {
                logger.info("Call {} does not ring: the callee has no devices", call.id());
                return;
            }
            Map<String, String> data = pushData(PUSH_INCOMING, call);
            data.put("ringTimeoutSec", Integer.toString(RING_TIMEOUT_SEC));
            DevicePusher.Outcome outcome = pusher.send(call.callee(), tokens, data, Duration.ofSeconds(RING_TIMEOUT_SEC));
            logger.info("Call {} ({}) rings {} of {} device(s)", call.id(), call.kind(), outcome.sent(), tokens.size());
        } catch (RuntimeException e) {
            logger.warn("Call {} could not ring: {}", call.id(), e.toString());
        }
    }

    /** Tells the callee's devices that a call stopped ringing and why. Best effort. */
    private void pushEnded(Call call, String reason) {
        boolean missed = REASON_MISSED.equals(reason) || REASON_CANCELLED.equals(reason);
        try {
            if (mutedByCallee(call)) {
                return;
            }
            List<String> tokens = pusher.tokens(call.callee());
            if (tokens.isEmpty()) {
                return;
            }
            Map<String, String> data = pushData(PUSH_ENDED, call);
            data.put("reason", reason);
            pusher.send(call.callee(), tokens, data, missed ? MISSED_PUSH_TTL : ENDED_PUSH_TTL);
        } catch (RuntimeException e) {
            logger.warn("Call {}: could not tell the callee's devices it is {}: {}", call.id(), reason, e.toString());
        }
    }

    private Map<String, String> pushData(String type, Call call) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("callId", call.id());
        data.put("kind", call.kind());
        data.put("caller", call.caller());
        DevicePusher.Profile profile = pusher.profile(call.caller());
        data.put("callerName", profile.name());
        data.put("callerAvatar", profile.avatar());
        // a phone that still holds another account's push token must not ring
        data.put("callee", call.callee());
        data.put("conversationId", call.conversationId());
        data.put("sentAt", Long.toString(call.createdAt().toEpochMilli()));
        return data;
    }

    private boolean mutedByCallee(Call call) {
        return firestore.getDocument("conversations", call.conversationId())
                .map(conversation -> conversation.get("muted") instanceof Map<?, ?> muted
                        && Boolean.TRUE.equals(muted.get(call.callee())))
                .orElse(false);
    }

    // ------------------------------------------------------------------ helpers

    /** The inbox preview and the thread row's text, the same wording the app uses. */
    static String preview(String status, String kind) {
        boolean video = VIDEO.equals(kind);
        if (ENDED.equals(status)) {
            return video ? "Video call" : "Audio call";
        }
        return (DECLINED.equals(status) ? "Declined " : "Missed ") + (video ? "video call" : "audio call");
    }

    /** Same id the app builds: the two usernames in ascending order, joined by "__". */
    static String conversationId(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "__" + b : b + "__" + a;
    }

    private static List<String> sorted(String a, String b) {
        return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
    }

    private static boolean isUsername(String name) {
        return FirestoreRestClient.isDocumentId(name) && name.length() <= MAX_USERNAME;
    }

    private Instant now() {
        // Firestore keeps microseconds; whole milliseconds read back exactly as written
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw CallException.notConfigured();
        }
    }

    private boolean overLimit(String caller, Instant now) {
        long minute = now.getEpochSecond() / 60;
        if (starts.size() > MAX_REMEMBERED) {
            starts.values().removeIf(window -> window.minute() < minute);
        }
        Window window = starts.merge(caller, new Window(minute, 1),
                (old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
        return window.count() > MAX_STARTS_PER_MINUTE;
    }

    /** The call {@code user} is a party of; 404 when there is none, 403 when it is someone else's. */
    private Call require(String callId, String user) {
        if (callId == null || !CALL_ID.matcher(callId).matches()) {
            throw ChatException.notFound("Call not found");
        }
        Call call = load(callId).orElseThrow(() -> ChatException.notFound("Call not found"));
        if (!call.caller().equals(user) && !call.callee().equals(user)) {
            throw ChatException.forbidden("You are not part of this call");
        }
        return call;
    }

    private Optional<Call> load(String callId) {
        return firestore.getSnapshot("calls", callId).flatMap(snapshot -> parse(callId, snapshot));
    }

    private static Optional<Call> parse(String id, Snapshot snapshot) {
        Map<String, Object> fields = snapshot.fields();
        String caller = text(fields, "caller");
        String callee = text(fields, "callee");
        String conversationId = text(fields, "conversationId");
        String kind = text(fields, "kind");
        String status = text(fields, "status");
        if (caller.isEmpty() || callee.isEmpty() || conversationId.isEmpty() || !STATUSES.contains(status)
                || !(fields.get("createdAt") instanceof Instant createdAt)) {
            logger.warn("Call {} has an unreadable document", id);
            return Optional.empty();
        }
        Instant answeredAt = fields.get("answeredAt") instanceof Instant instant ? instant : null;
        long duration = fields.get("durationSec") instanceof Number number ? number.longValue() : 0;
        return Optional.of(new Call(id, caller, callee, conversationId, VIDEO.equals(kind) ? VIDEO : AUDIO, status,
                createdAt, answeredAt, duration, snapshot.updateTime()));
    }

    private static String text(Map<String, Object> fields, String name) {
        return fields.get(name) instanceof String value ? value : "";
    }

    /** The call with what {@code user} needs to join its channel: the token is theirs alone (channel + uid). */
    private CallSession joinable(Call call, String user, boolean rings) {
        int uid = user.equals(call.caller()) ? CALLER_UID : CALLEE_UID;
        String token = agora.rtcToken(call.id(), uid, TOKEN_VALIDITY);
        return new CallSession(call.id(), call.status(), call.kind(), call.caller(), call.callee(),
                call.conversationId(), call.durationSec(), call.id(), agora.appId(), token, uid,
                (int) TOKEN_VALIDITY.getSeconds(), rings ? RING_TIMEOUT_SEC : null);
    }

    private static CallSession summary(Call call) {
        return new CallSession(call.id(), call.status(), call.kind(), call.caller(), call.callee(),
                call.conversationId(), call.durationSec(), null, null, null, null, null, null);
    }
}
