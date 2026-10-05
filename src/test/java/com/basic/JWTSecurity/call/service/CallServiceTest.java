package com.basic.JWTSecurity.call.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.service.ChatException;
import com.basic.JWTSecurity.chat.service.FakeFirebase;
import com.basic.JWTSecurity.chat.service.FakeFirebase.Push;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agora.media.AccessToken2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// CallService against FakeFirebase: the real Firestore / FCM clients talking to an in-memory Firestore that honours
// commit preconditions, so the call's lifecycle and its races run as they do in production.
class CallServiceTest {

    // made-up keys in the format Agora uses (32 hex characters)
    private static final String APP_ID = "0123456789abcdef0123456789abcdef";
    private static final String CERTIFICATE = "fedcba9876543210fedcba9876543210";
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final String THREAD = "conversations/alice__bob";

    private FakeFirebase firebase;
    private TestClock clock;
    private ManualTimer timer;
    private CallService calls;

    @BeforeEach
    void setUp() {
        firebase = new FakeFirebase();
        clock = new TestClock(NOW);
        timer = new ManualTimer();
        calls = service(new AgoraTokens(APP_ID, CERTIFICATE), firebase.credentials());
        firebase.user("alice", "Alice A", "https://img.example/alice.jpg");
        firebase.user("bob", "Bob B", "");
        firebase.devices("alice", "alice-phone");
        firebase.devices("bob", "bob-phone", "bob-tablet");
    }

    private CallService service(AgoraTokens agora, ChatFirebaseCredentials credentials) {
        return new CallService(agora, credentials, firebase.firestore(), firebase.pusher(), clock, timer);
    }

    // ---- start

    @Test
    void startCreatesARingingCallAndRingsEveryDeviceOfTheCallee() {
        CallSession session = calls.start("alice", "bob", "video");

        String id = session.callId();
        assertEquals("ringing", session.status());
        assertEquals("video", session.kind());
        assertEquals("alice", session.caller());
        assertEquals("bob", session.callee());
        assertEquals("alice__bob", session.conversationId());
        assertEquals(id, session.channel());
        assertEquals(APP_ID, session.appId());
        assertEquals(1, session.uid());
        assertEquals(3600, session.expiresInSec());
        assertEquals(45, session.ringTimeoutSec());
        assertTokenFor(session.token(), id, "1");
        assertFalse(session.toString().contains(session.token()));

        Map<String, Object> call = firebase.fields("calls/" + id);
        assertEquals(List.of("alice", "bob"), call.get("usernames"));
        assertEquals("alice__bob", call.get("conversationId"));
        assertEquals("alice", call.get("caller"));
        assertEquals("bob", call.get("callee"));
        assertEquals("video", call.get("kind"));
        assertEquals("ringing", call.get("status"));
        assertEquals(NOW, call.get("createdAt"));
        assertNull(call.get("answeredAt"));
        assertNull(call.get("endedAt"));
        assertEquals(0L, call.get("durationSec"));

        assertEquals(2, firebase.pushes().size());
        assertEquals(List.of("bob-phone", "bob-tablet"), firebase.pushes().stream().map(Push::token).toList());
        Push push = firebase.pushes().get(0);
        assertEquals("45s", push.ttl());
        assertEquals("HIGH", push.priority());
        assertEquals(Map.of(
                "type", "incoming_call",
                "callId", id,
                "kind", "video",
                "caller", "alice",
                "callerName", "Alice A",
                "callerAvatar", "https://img.example/alice.jpg",
                "callee", "bob",
                "conversationId", "alice__bob",
                "sentAt", Long.toString(NOW.toEpochMilli()),
                "ringTimeoutSec", "45"), push.data());
        // nothing in the thread until the call is over
        assertNull(firebase.fields(THREAD));
        assertNull(firebase.fields(THREAD + "/messages/" + id));
    }

    @Test
    void startValidatesItsInput() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", "bob", "screen"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", "bob", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", " ", "audio"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", null, "audio"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", "bob/../carol", "audio"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> calls.start("alice", "alice", "audio"));
        // nobody with that name has ever opened chat
        assertStatus(HttpStatus.NOT_FOUND, () -> calls.start("alice", "nobody", "audio"));

        assertTrue(firebase.paths("calls").isEmpty());
        assertTrue(firebase.pushes().isEmpty());
    }

    @Test
    void aRetriedStartGetsTheSameCallAndCallingSomeoneElseCancelsTheFirst() {
        firebase.user("carol", "Carol", "");
        firebase.devices("carol", "carol-phone");

        String first = calls.start("alice", "bob", "video").callId();
        CallSession again = calls.start("alice", "bob", "video");
        assertEquals(first, again.callId());
        assertEquals(45, again.ringTimeoutSec());
        assertTokenFor(again.token(), first, "1");
        // bob was not rung a second time
        assertEquals(2, firebase.pushes().size());
        assertEquals(1, firebase.paths("calls").size());

        String second = calls.start("alice", "carol", "audio").callId();
        assertNotEquals(first, second);
        assertEquals("cancelled", firebase.fields("calls/" + first).get("status"));
        assertEquals("ringing", firebase.fields("calls/" + second).get("status"));
        assertEquals(List.of("cancelled", "cancelled"), ended("bob-phone", "bob-tablet"));
        assertEquals("incoming_call", firebase.pushes().get(firebase.pushes().size() - 1).data().get("type"));
        assertEquals("carol-phone", firebase.pushes().get(firebase.pushes().size() - 1).token());
    }

    @Test
    void tooManyCallsInAMinuteAre429() {
        for (int i = 0; i < CallService.MAX_STARTS_PER_MINUTE; i++) {
            calls.start("alice", "bob", "audio");
        }
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> calls.start("alice", "bob", "audio"));
        // someone else is not affected, and a minute later neither is alice
        firebase.user("carol", "Carol", "");
        calls.start("bob", "carol", "audio");
        clock.advance(Duration.ofSeconds(61));
        calls.start("alice", "bob", "audio");
    }

    @Test
    void aMutedChatDoesNotRingButStillLogsTheMissedCall() {
        firebase.conversation("alice", "bob", Map.of(), Map.of("bob", true));

        String id = calls.start("alice", "bob", "audio").callId();
        calls.cancel("alice", id, false);

        assertTrue(firebase.pushes().isEmpty());
        assertEquals("Missed audio call", firebase.fields(THREAD + "/messages/" + id).get("text"));
        assertEquals(Map.of("bob", 1L), firebase.fields(THREAD).get("unread"));
    }

    @Test
    void devicesThatUninstalledTheAppAreForgotten() {
        firebase.uninstall("bob-tablet");

        calls.start("alice", "bob", "audio");

        assertEquals(List.of("bob-phone"), firebase.pushes().stream().map(Push::token).toList());
        assertEquals(List.of("bob-phone"), firebase.fields("users/bob/private/devices").get("tokens"));
    }

    // ---- answer and hang up

    @Test
    void answeringJoinsTheCalleeAsUidTwoAndStopsTheirOtherDevices() {
        String id = calls.start("alice", "bob", "video").callId();
        firebase.pushes().clear();
        clock.advance(Duration.ofSeconds(5));

        CallSession session = calls.accept("bob", id);

        assertEquals("accepted", session.status());
        assertEquals(2, session.uid());
        assertEquals(id, session.channel());
        assertEquals(APP_ID, session.appId());
        assertNull(session.ringTimeoutSec());
        assertTokenFor(session.token(), id, "2");
        Map<String, Object> call = firebase.fields("calls/" + id);
        assertEquals("accepted", call.get("status"));
        assertEquals(NOW.plusSeconds(5), call.get("answeredAt"));
        assertNull(call.get("endedAt"));

        // sent after the answer, off the request's thread
        assertTrue(firebase.pushes().isEmpty());
        timer.run(Duration.ZERO);
        assertEquals(List.of("answered", "answered"), ended("bob-phone", "bob-tablet"));
        assertEquals("60s", firebase.pushes().get(0).ttl());
        assertEquals("bob", firebase.pushes().get(0).data().get("callee"));

        // a repeated request is answered the same way, without another write
        int commits = firebase.commits();
        assertEquals("accepted", calls.accept("bob", id).status());
        assertEquals(commits, firebase.commits());

        assertStatus(HttpStatus.FORBIDDEN, () -> calls.accept("alice", id));
        assertStatus(HttpStatus.FORBIDDEN, () -> calls.accept("carol", id));
        assertStatus(HttpStatus.NOT_FOUND, () -> calls.accept("bob", "nosuchcall"));
    }

    @Test
    void hangingUpWritesTheCallIntoTheThreadWithItsDuration() {
        String id = calls.start("alice", "bob", "video").callId();
        clock.advance(Duration.ofSeconds(5));
        calls.accept("bob", id);
        timer.run(Duration.ZERO);
        firebase.pushes().clear();
        clock.advance(Duration.ofSeconds(125));

        CallSession over = calls.end("bob", id);

        assertEquals("ended", over.status());
        assertEquals(125, over.durationSec());
        assertNull(over.token());
        Instant endedAt = NOW.plusSeconds(130);
        Map<String, Object> call = firebase.fields("calls/" + id);
        assertEquals("ended", call.get("status"));
        assertEquals(endedAt, call.get("endedAt"));
        assertEquals("bob", call.get("endedBy"));
        assertEquals(125L, call.get("durationSec"));
        assertEquals(NOW.plusSeconds(5), call.get("answeredAt"));

        // the row in the thread belongs to the caller, whoever hung up
        Map<String, Object> message = firebase.fields(THREAD + "/messages/" + id);
        assertEquals("alice", message.get("sender"));
        assertEquals("call", message.get("type"));
        assertEquals("Video call", message.get("text"));
        assertEquals(Map.of("id", id, "kind", "video", "outcome", "completed", "durationSec", 125L), message.get("call"));
        assertEquals(Map.of(), message.get("reactions"));
        assertEquals(false, message.get("unsent"));
        assertEquals(endedAt, message.get("createdAt"));

        // these two never chatted: the conversation is created the way the app creates it
        Map<String, Object> conversation = firebase.fields(THREAD);
        assertEquals(List.of("alice", "bob"), conversation.get("usernames"));
        assertEquals(endedAt, conversation.get("createdAt"));
        assertEquals(endedAt, conversation.get("updatedAt"));
        assertEquals(Map.of("id", id, "sender", "alice", "type", "call", "preview", "Video call", "createdAt", endedAt),
                conversation.get("lastMessage"));
        for (String field : List.of("unread", "lastRead", "typing", "muted", "markedUnread", "clearedAt")) {
            assertEquals(Map.of(), conversation.get(field), field);
        }
        // both phones are in the call and see it end: nobody is pushed
        assertTrue(firebase.pushes().isEmpty());

        // hanging up twice (the other side, a retry) changes nothing
        int commits = firebase.commits();
        assertEquals(125, calls.end("alice", id).durationSec());
        assertEquals("ended", calls.cancel("alice", id, false).status());
        assertEquals("ended", calls.decline("bob", id, false).status());
        assertEquals(commits, firebase.commits());
        assertStatus(HttpStatus.FORBIDDEN, () -> calls.end("carol", id));
    }

    // ---- not answered

    @Test
    void decliningIsLoggedButNotUnread() {
        firebase.conversation("alice", "bob", Map.of("bob", 2L), Map.of());
        String id = calls.start("alice", "bob", "audio").callId();
        firebase.pushes().clear();
        assertStatus(HttpStatus.FORBIDDEN, () -> calls.decline("alice", id, false));

        CallSession over = calls.decline("bob", id, false);

        assertEquals("declined", over.status());
        assertEquals(0, over.durationSec());
        Map<String, Object> call = firebase.fields("calls/" + id);
        assertEquals("declined", call.get("status"));
        assertEquals("bob", call.get("endedBy"));
        Map<String, Object> message = firebase.fields(THREAD + "/messages/" + id);
        assertEquals("Declined audio call", message.get("text"));
        assertEquals(Map.of("id", id, "kind", "audio", "outcome", "declined", "durationSec", 0L), message.get("call"));
        Map<String, Object> conversation = firebase.fields(THREAD);
        assertEquals("Declined audio call", ((Map<?, ?>) conversation.get("lastMessage")).get("preview"));
        assertEquals(NOW, conversation.get("updatedAt"));
        // bob saw the call: his unread count stays, and the rest of the conversation is untouched
        assertEquals(Map.of("bob", 2L), conversation.get("unread"));
        assertEquals(List.of("alice", "bob"), conversation.get("usernames"));
        assertEquals(Map.of(), conversation.get("muted"));
        // bob's other devices stop ringing
        assertEquals(List.of("declined", "declined"), ended("bob-phone", "bob-tablet"));
        assertEquals("60s", firebase.pushes().get(0).ttl());

        assertEquals("declined", calls.decline("bob", id, false).status());
        assertConflict("declined", () -> calls.accept("bob", id));
    }

    @Test
    void aBusyPhoneCountsAsAMissedCall() {
        String id = calls.start("alice", "bob", "audio").callId();
        firebase.pushes().clear();

        assertEquals("busy", calls.decline("bob", id, true).status());

        Map<String, Object> message = firebase.fields(THREAD + "/messages/" + id);
        assertEquals("Missed audio call", message.get("text"));
        assertEquals("busy", ((Map<?, ?>) message.get("call")).get("outcome"));
        assertEquals(Map.of("bob", 1L), firebase.fields(THREAD).get("unread"));
        assertEquals(List.of("missed", "missed"), ended("bob-phone", "bob-tablet"));
        assertEquals("86400s", firebase.pushes().get(0).ttl());
    }

    @Test
    void theCallerGivingUpIsAMissedCallForTheCallee() {
        firebase.conversation("alice", "bob", Map.of("bob", 3L, "alice", 1L), Map.of());
        String id = calls.start("alice", "bob", "video").callId();
        firebase.pushes().clear();
        assertStatus(HttpStatus.FORBIDDEN, () -> calls.cancel("bob", id, false));

        assertEquals("cancelled", calls.cancel("alice", id, false).status());

        assertEquals("alice", firebase.fields("calls/" + id).get("endedBy"));
        Map<String, Object> message = firebase.fields(THREAD + "/messages/" + id);
        assertEquals("Missed video call", message.get("text"));
        assertEquals("cancelled", ((Map<?, ?>) message.get("call")).get("outcome"));
        assertEquals(Map.of("bob", 4L, "alice", 1L), firebase.fields(THREAD).get("unread"));
        // kept for a day so a phone that was offline still shows the missed call
        assertEquals(List.of("cancelled", "cancelled"), ended("bob-phone", "bob-tablet"));
        assertEquals("86400s", firebase.pushes().get(0).ttl());
        assertEquals("Alice A", firebase.pushes().get(0).data().get("callerName"));
        assertEquals("video", firebase.pushes().get(0).data().get("kind"));

        assertEquals("cancelled", calls.cancel("alice", id, true).status());
        assertConflict("cancelled", () -> calls.accept("bob", id));
    }

    @Test
    void ringingOutOnTheCallersPhoneIsMissed() {
        String id = calls.start("alice", "bob", "audio").callId();
        firebase.pushes().clear();
        clock.advance(Duration.ofSeconds(45));

        assertEquals("missed", calls.cancel("alice", id, true).status());

        assertEquals("missed", ((Map<?, ?>) firebase.fields(THREAD + "/messages/" + id).get("call")).get("outcome"));
        assertEquals(List.of("missed", "missed"), ended("bob-phone", "bob-tablet"));
        // the timer that would have expired the call finds nothing left to do
        int commits = firebase.commits();
        timer.run(CallService.RING_EXPIRY);
        assertEquals(commits, firebase.commits());
    }

    @Test
    void anUnansweredCallExpiresOnItsOwn() {
        // alice's app died after placing the call: nobody cancels it
        String id = calls.start("alice", "bob", "video").callId();
        firebase.pushes().clear();
        clock.advance(CallService.RING_EXPIRY);

        timer.run(CallService.RING_EXPIRY);

        Map<String, Object> call = firebase.fields("calls/" + id);
        assertEquals("missed", call.get("status"));
        assertNull(call.get("endedBy"));
        assertEquals("Missed video call", firebase.fields(THREAD + "/messages/" + id).get("text"));
        assertEquals(Map.of("bob", 1L), firebase.fields(THREAD).get("unread"));
        assertEquals(List.of("missed", "missed"), ended("bob-phone", "bob-tablet"));
        // and alice can call again
        assertNotEquals(id, calls.start("alice", "bob", "video").callId());
    }

    @Test
    void anAnsweredCallDoesNotExpire() {
        String id = calls.start("alice", "bob", "video").callId();
        calls.accept("bob", id);
        clock.advance(CallService.RING_EXPIRY);

        timer.run(CallService.RING_EXPIRY);

        assertEquals("accepted", firebase.fields("calls/" + id).get("status"));
        assertNull(firebase.fields(THREAD + "/messages/" + id));
    }

    @Test
    void answeringTooLateIs409AndTheCallIsMissed() {
        String id = calls.start("alice", "bob", "audio").callId();
        firebase.pushes().clear();
        clock.advance(CallService.RING_EXPIRY);

        assertConflict("missed", () -> calls.accept("bob", id));

        assertEquals("missed", firebase.fields("calls/" + id).get("status"));
        assertEquals("Missed audio call", firebase.fields(THREAD + "/messages/" + id).get("text"));
        assertEquals(List.of("missed", "missed"), ended("bob-phone", "bob-tablet"));
    }

    @Test
    void hangingUpARingingCallCancelsOrDeclinesIt() {
        String first = calls.start("alice", "bob", "audio").callId();
        assertEquals("cancelled", calls.end("alice", first).status());

        String second = calls.start("alice", "bob", "audio").callId();
        assertEquals("declined", calls.end("bob", second).status());
        assertEquals("Declined audio call", firebase.fields(THREAD + "/messages/" + second).get("text"));
        // only the call bob never saw through counts as unread
        assertEquals(Map.of("bob", 1L), firebase.fields(THREAD).get("unread"));
    }

    // ---- two phones at the same moment

    @Test
    void anAnswerThatLosesToTheCallerHangingUpIs409() {
        String id = calls.start("alice", "bob", "video").callId();
        // alice hangs up between bob's phone reading the call and writing its answer
        firebase.beforeNextCommit(() -> calls.cancel("alice", id, false));

        assertConflict("cancelled", () -> calls.accept("bob", id));

        assertEquals("cancelled", firebase.fields("calls/" + id).get("status"));
        assertNull(firebase.fields("calls/" + id).get("answeredAt"));
        assertEquals("cancelled", ((Map<?, ?>) firebase.fields(THREAD + "/messages/" + id).get("call")).get("outcome"));
    }

    @Test
    void aCancelThatLosesToTheAnswerEndsTheCall() {
        String id = calls.start("alice", "bob", "video").callId();
        // bob answers between alice's phone reading the call and writing the cancel
        firebase.beforeNextCommit(() -> calls.accept("bob", id));
        clock.advance(Duration.ofSeconds(3));

        CallSession over = calls.cancel("alice", id, false);

        // alice is leaving either way, so the call bob just answered is over; it is not a missed call
        assertEquals("ended", over.status());
        Map<String, Object> message = firebase.fields(THREAD + "/messages/" + id);
        assertEquals("Video call", message.get("text"));
        assertEquals("completed", ((Map<?, ?>) message.get("call")).get("outcome"));
        assertEquals(Map.of(), firebase.fields(THREAD).get("unread"));
    }

    @Test
    void decliningACallAnsweredOnAnotherDeviceLeavesItRunning() {
        String id = calls.start("alice", "bob", "video").callId();
        calls.accept("bob", id);

        assertConflict("accepted", () -> calls.decline("bob", id, false));

        assertEquals("accepted", firebase.fields("calls/" + id).get("status"));
        assertNull(firebase.fields(THREAD + "/messages/" + id));
    }

    @Test
    void bothSidesHangingUpAtOnceLogsTheCallOnce() {
        String id = calls.start("alice", "bob", "audio").callId();
        calls.accept("bob", id);
        clock.advance(Duration.ofSeconds(60));
        firebase.beforeNextCommit(() -> calls.end("bob", id));

        CallSession over = calls.end("alice", id);

        assertEquals("ended", over.status());
        assertEquals(60, over.durationSec());
        assertEquals("bob", firebase.fields("calls/" + id).get("endedBy"));
        assertEquals(1, firebase.paths(THREAD + "/messages").size());
    }

    // ---- tokens and configuration

    @Test
    void aFreshTokenIsOnlyForThePartiesOfALiveCall() {
        String id = calls.start("alice", "bob", "audio").callId();

        assertTokenFor(calls.token("alice", id).token(), id, "1");
        // the callee has to answer first
        assertConflict("ringing", () -> calls.token("bob", id));
        assertStatus(HttpStatus.FORBIDDEN, () -> calls.token("carol", id));
        assertStatus(HttpStatus.NOT_FOUND, () -> calls.token("alice", "nosuchcall"));
        assertStatus(HttpStatus.NOT_FOUND, () -> calls.token("alice", "../users/alice"));

        calls.accept("bob", id);
        CallSession refreshed = calls.token("bob", id);
        assertEquals(2, refreshed.uid());
        assertEquals("accepted", refreshed.status());
        assertTokenFor(refreshed.token(), id, "2");

        calls.end("alice", id);
        assertConflict("ended", () -> calls.token("alice", id));
        assertConflict("ended", () -> calls.token("bob", id));
    }

    @Test
    void withoutAgoraKeysCallsAre503() {
        for (AgoraTokens agora : List.of(new AgoraTokens("", ""), new AgoraTokens(APP_ID, " "),
                new AgoraTokens(null, null), new AgoraTokens("not-an-app-id", CERTIFICATE), new AgoraTokens(APP_ID, "short"))) {
            CallService unconfigured = service(agora, firebase.credentials());

            assertFalse(agora.isConfigured());
            assertEquals("", agora.appId());
            assertEquals(new CallConfig(false, null, 45), unconfigured.config());
            assertNotConfigured(() -> unconfigured.start("alice", "bob", "audio"));
            assertNotConfigured(() -> unconfigured.accept("bob", "abc"));
            assertNotConfigured(() -> unconfigured.decline("bob", "abc", false));
            assertNotConfigured(() -> unconfigured.cancel("alice", "abc", false));
            assertNotConfigured(() -> unconfigured.end("alice", "abc"));
            assertNotConfigured(() -> unconfigured.token("alice", "abc"));
            assertNotConfigured(() -> agora.rtcToken("abc", 1, Duration.ofHours(1)));
        }
        assertTrue(firebase.paths("calls").isEmpty());
        assertTrue(firebase.pushes().isEmpty());
    }

    @Test
    void withoutTheFirebaseAccountCallsAre503() {
        CallService unconfigured = service(new AgoraTokens(APP_ID, CERTIFICATE),
                new ChatFirebaseCredentials(FakeFirebase.PROJECT, "", new ObjectMapper()));

        assertFalse(unconfigured.config().enabled());
        assertNotConfigured(() -> unconfigured.start("alice", "bob", "audio"));
    }

    @Test
    void configuredCallsTellTheAppThePublicAppId() {
        assertEquals(new CallConfig(true, APP_ID, 45), calls.config());
        // keys pasted with surrounding whitespace still work
        assertEquals(APP_ID, new AgoraTokens(" " + APP_ID + "\n", CERTIFICATE + " ").appId());
    }

    @Test
    void aFirestoreOutageIs502AndNobodyIsRung() {
        firebase.unreachable(true);

        assertStatus(HttpStatus.BAD_GATEWAY, () -> calls.start("alice", "bob", "audio"));

        firebase.unreachable(false);
        assertTrue(firebase.paths("calls").isEmpty());
        assertTrue(firebase.pushes().isEmpty());
    }

    // ---- details

    @Test
    void usernamesThatAreNotPlainIdentifiersAreCountedUnreadToo() {
        firebase.user("john.doe", "John", "");
        firebase.user("a-b", "A B", "");
        firebase.conversation("a-b", "john.doe", Map.of("john.doe", 1L), Map.of());

        String id = calls.start("a-b", "john.doe", "audio").callId();
        calls.cancel("a-b", id, false);

        assertEquals("a-b__john.doe", firebase.fields("calls/" + id).get("conversationId"));
        assertEquals(Map.of("john.doe", 2L), firebase.fields("conversations/a-b__john.doe").get("unread"));
    }

    @Test
    void theCalleeSortingFirstStillGetsTheCallersRow() {
        // bob calls alice: the conversation id and usernames stay sorted, the row is bob's
        String id = calls.start("bob", "alice", "audio").callId();
        calls.cancel("bob", id, false);

        assertEquals(List.of("alice", "bob"), firebase.fields("calls/" + id).get("usernames"));
        assertEquals("bob", firebase.fields(THREAD + "/messages/" + id).get("sender"));
        assertEquals(Map.of("alice", 1L), firebase.fields(THREAD).get("unread"));
        assertEquals("alice-phone", firebase.pushes().get(0).token());
    }

    @Test
    void previewsUseTheWordingOfTheApp() {
        assertEquals("Audio call", CallService.preview("ended", "audio"));
        assertEquals("Video call", CallService.preview("ended", "video"));
        assertEquals("Missed audio call", CallService.preview("missed", "audio"));
        assertEquals("Missed video call", CallService.preview("cancelled", "video"));
        assertEquals("Missed video call", CallService.preview("busy", "video"));
        assertEquals("Declined audio call", CallService.preview("declined", "audio"));
        assertEquals("Declined video call", CallService.preview("declined", "video"));
        assertEquals("a__b", CallService.conversationId("b", "a"));
        assertEquals("a__b", CallService.conversationId("a", "b"));
    }

    // ---- helpers

    /** The reasons of the call_ended pushes, which must have gone to exactly these devices in this order. */
    private List<String> ended(String... tokens) {
        List<Push> ended = firebase.pushes().stream().filter(push -> "call_ended".equals(push.data().get("type"))).toList();
        assertEquals(List.of(tokens), ended.stream().map(Push::token).toList());
        return ended.stream().map(push -> push.data().get("reason")).toList();
    }

    /** An Agora RTC token of our app for exactly this channel and uid, valid for an hour. */
    private static void assertTokenFor(String token, String channel, String uid) {
        assertNotNull(token);
        AccessToken2 parsed = new AccessToken2();
        assertTrue(parsed.parse(token), "not an Agora token");
        assertEquals(APP_ID, parsed.appId);
        assertEquals(3600, parsed.expire);
        AccessToken2.ServiceRtc rtc = (AccessToken2.ServiceRtc) parsed.services.get(AccessToken2.SERVICE_TYPE_RTC);
        assertNotNull(rtc, "no RTC service in the token");
        assertEquals(channel, rtc.getChannelName());
        assertEquals(uid, rtc.getUid());
        // the certificate signs the token; it is not in it
        assertFalse(token.contains(CERTIFICATE));
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        ChatException e = assertThrows(ChatException.class, call::run);
        assertEquals(expected, e.getStatus(), e.getMessage());
    }

    private static void assertConflict(String callStatus, Runnable call) {
        CallException e = assertThrows(CallException.class, call::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatus(), e.getMessage());
        assertEquals(callStatus, e.getCallStatus());
    }

    private static void assertNotConfigured(Runnable call) {
        CallException e = assertThrows(CallException.class, call::run);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("Calls are not configured yet", e.getMessage());
    }

    private static final class ManualTimer implements CallService.Timer {
        private record Task(Runnable task, Duration delay) {
        }

        private final List<Task> tasks = new ArrayList<>();

        @Override
        public void schedule(Runnable task, Duration delay) {
            tasks.add(new Task(task, delay));
        }

        /** Runs (and forgets) the tasks that were scheduled with this delay. */
        void run(Duration delay) {
            List<Task> due = tasks.stream().filter(task -> task.delay().equals(delay)).toList();
            tasks.removeAll(due);
            due.forEach(task -> task.task().run());
        }
    }

    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
