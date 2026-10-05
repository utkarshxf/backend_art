package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials.ServiceAccount;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat is realtime in Firestore; the backend only (1) signs the app into Firebase as the logged-in user (custom token,
 * uid == username == JWT subject) and (2) sends the push for a message the caller just wrote, after checking it in
 * Firestore with admin rights. Firestore layout: see the chat spec (conversations/{cid}, .../messages/{mid},
 * users/{username}, users/{username}/private/devices).
 */
@Service
public class ChatService {

    static final String FIREBASE_AUDIENCE =
            "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";
    /** Firebase Auth limit for a uid. */
    static final int MAX_UID_LENGTH = 128;
    /** Only fresh messages are pushed, so a caller cannot replay old ones at the peer. */
    static final Duration MAX_MESSAGE_AGE = Duration.ofMinutes(15);
    static final int PREVIEW_LENGTH = 120;
    private static final int MAX_REMEMBERED = 10_000;
    private static final String LIKE = "❤️";

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatFirebaseCredentials credentials;
    private final FirestoreRestClient firestore;
    private final DevicePusher pusher;
    private final Clock clock;

    /** conversationId/messageId already pushed (or being pushed) → when, so each message is pushed once. */
    private final Map<String, Instant> notified = new ConcurrentHashMap<>();

    @Autowired
    public ChatService(ChatFirebaseCredentials credentials, FirestoreRestClient firestore, DevicePusher pusher) {
        this(credentials, firestore, pusher, Clock.systemUTC());
    }

    ChatService(ChatFirebaseCredentials credentials, FirestoreRestClient firestore, DevicePusher pusher, Clock clock) {
        this.credentials = credentials;
        this.firestore = firestore;
        this.pusher = pusher;
        this.clock = clock;
    }

    /**
     * A Firebase custom token for {@code username}: RS256 with the service-account key, valid for one hour (the app
     * exchanges it right away with signInWithCustomToken; Firebase then keeps the session refreshed).
     */
    public String mintCustomToken(String username) {
        ServiceAccount account = credentials.serviceAccount().orElseThrow(ChatException::notConfigured);
        if (username == null || username.isBlank() || username.length() > MAX_UID_LENGTH) {
            throw ChatException.badRequest("This account cannot use chat");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .header().type("JWT").and()
                .issuer(account.clientEmail())
                .subject(account.clientEmail())
                .audience().single(FIREBASE_AUDIENCE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", username)
                .signWith(account.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Pushes message {@code messageId} of conversation {@code conversationId} to the other member's devices and
     * returns how many devices FCM accepted it for. The caller must be a member and the message's sender. Returns 0
     * without sending when the recipient muted the conversation, has no devices, the message was unsent or is older
     * than {@link #MAX_MESSAGE_AGE}, or it was already pushed. Tokens FCM reports as dead are removed.
     */
    public int notifyRecipient(String caller, String conversationId, String messageId) {
        credentials.serviceAccount().orElseThrow(ChatException::notConfigured);
        requireDocumentId(conversationId, "conversationId");
        requireDocumentId(messageId, "messageId");
        // conversation ids are "<first>__<second>" (sorted usernames), so a member is always a prefix or a suffix;
        // this answers most forged requests without touching Firestore
        if (caller == null || !(conversationId.startsWith(caller + "__") || conversationId.endsWith("__" + caller))) {
            throw notAMember();
        }

        Map<String, Object> conversation = firestore.getDocument("conversations", conversationId)
                .orElseThrow(() -> ChatException.notFound("Conversation not found"));
        List<String> usernames = strings(conversation.get("usernames"));
        if (!usernames.contains(caller)) {
            throw notAMember();
        }
        if (usernames.size() != 2 || usernames.get(0).equals(usernames.get(1))) {
            throw ChatException.badRequest("Invalid conversation");
        }
        String recipient = usernames.get(0).equals(caller) ? usernames.get(1) : usernames.get(0);

        Map<String, Object> message = firestore.getDocument("conversations", conversationId, "messages", messageId)
                .orElseThrow(() -> ChatException.notFound("Message not found"));
        if (!caller.equals(message.get("sender"))) {
            throw ChatException.forbidden("You can only send notifications for your own messages");
        }
        // a call's row in the thread is written by the backend (CallService), which sends its own pushes
        if (Boolean.TRUE.equals(message.get("unsent")) || "call".equals(message.get("type"))) {
            return 0;
        }
        Instant now = clock.instant();
        Instant createdAt = message.get("createdAt") instanceof Instant instant ? instant : null;
        if (createdAt == null || createdAt.isBefore(now.minus(MAX_MESSAGE_AGE))) {
            logger.info("Chat push for {}/{} skipped: the message is not recent", conversationId, messageId);
            return 0;
        }
        if (Boolean.TRUE.equals(map(conversation.get("muted")).get(recipient))) {
            return 0;
        }

        String key = conversationId + "/" + messageId;
        if (!claim(key, now)) {
            return 0;
        }
        try {
            return push(caller, recipient, conversationId, messageId, message);
        } catch (RuntimeException e) {
            // nothing (or not everything) was delivered: let the app retry
            notified.remove(key);
            throw e;
        }
    }

    private int push(String sender, String recipient, String conversationId, String messageId, Map<String, Object> message) {
        List<String> tokens = pusher.tokens(recipient);
        if (tokens.isEmpty()) {
            return 0;
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "chat_message");
        data.put("conversationId", conversationId);
        data.put("messageId", messageId);
        data.put("sender", sender);
        DevicePusher.Profile profile = pusher.profile(sender);
        data.put("senderName", profile.name());
        data.put("senderAvatar", profile.avatar());
        data.put("preview", preview(message));

        DevicePusher.Outcome outcome = pusher.send(recipient, tokens, data, null);
        logger.info("Chat push for {}/{}: sent to {} of {} device(s), removed {} dead token(s)",
                conversationId, messageId, outcome.sent(), tokens.size(), outcome.removed());
        if (outcome.sent() == 0 && outcome.failed() > 0) {
            throw ChatException.upstream();
        }
        return outcome.sent();
    }

    /** Same wording the app uses for lastMessage.preview. */
    static String preview(Map<String, Object> message) {
        String type = message.get("type") instanceof String t ? t : "";
        switch (type) {
            case "text": {
                String text = message.get("text") instanceof String body ? body.strip() : "";
                return text.isEmpty() ? "Sent a message" : truncate(text, PREVIEW_LENGTH);
            }
            case "image":
                return "Sent a photo";
            case "artwork":
                return "Shared a post";
            case "profile":
                return "Shared a profile";
            case "like":
                return LIKE;
            default:
                return "Sent a message";
        }
    }

    /** At most {@code max} characters without cutting an emoji (surrogate pair) in half. */
    static String truncate(String text, int max) {
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }

    private boolean claim(String key, Instant now) {
        if (notified.size() > MAX_REMEMBERED) {
            // older entries can go: their messages are past MAX_MESSAGE_AGE and would be refused anyway
            Instant cutoff = now.minus(MAX_MESSAGE_AGE);
            notified.values().removeIf(at -> at.isBefore(cutoff));
        }
        return notified.putIfAbsent(key, now) == null;
    }

    private static ChatException notAMember() {
        return ChatException.forbidden("You are not a member of this conversation");
    }

    /** A Firestore document id that is safe to put in a REST path. */
    private static void requireDocumentId(String id, String field) {
        if (!FirestoreRestClient.isDocumentId(id)) {
            throw ChatException.badRequest("Invalid " + field);
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof String s) {
                strings.add(s);
            }
        }
        return strings;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
