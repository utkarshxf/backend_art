package com.basic.JWTSecurity.chat.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pushes one FCM data message to every device of a user, for chat messages and for calls. Device tokens live in
 * {@code users/{username}/private/devices}; tokens FCM reports as dead are removed from it.
 */
@Component
public class DevicePusher {

    /** A user has a handful of devices; this bounds the work (and time) of one request. */
    static final int MAX_TOKENS = 20;

    /** What the notification shows for a user: display name (the username when there is none) and picture ("" if none). */
    public record Profile(String name, String avatar) {
    }

    /** How a push went: devices FCM accepted it for, devices it failed for (their tokens are kept), dead tokens removed. */
    public record Outcome(int sent, int failed, int removed) {
    }

    private final FirestoreRestClient firestore;
    private final FcmClient fcm;

    public DevicePusher(FirestoreRestClient firestore, FcmClient fcm) {
        this.firestore = firestore;
        this.fcm = fcm;
    }

    /** The user's registered device tokens: no blanks, no duplicates, at most {@link #MAX_TOKENS}. */
    public List<String> tokens(String username) {
        Object stored = firestore.getDocument("users", username, "private", "devices")
                .map(devices -> devices.get("tokens"))
                .orElse(null);
        Set<String> unique = new LinkedHashSet<>();
        if (stored instanceof List<?> list) {
            for (Object token : list) {
                if (token instanceof String s && !s.isBlank() && unique.size() < MAX_TOKENS) {
                    unique.add(s);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    /** users/{username} for a notification's title and picture; the push still goes out without it. */
    public Profile profile(String username) {
        Map<String, Object> profile;
        try {
            profile = firestore.getDocument("users", username).orElse(Map.of());
        } catch (ChatException e) {
            profile = Map.of();
        }
        String name = profile.get("name") instanceof String s ? s.trim() : "";
        String avatar = profile.get("avatar") instanceof String s ? s.trim() : "";
        return new Profile(name.isEmpty() ? username : name, avatar);
    }

    /**
     * Sends {@code data} to each of {@code tokens} (the recipient's, from {@link #tokens}) and removes the dead ones
     * from the recipient's devices document. {@code ttl} is how long FCM may keep the message for a device that is
     * offline; null leaves FCM's default.
     */
    public Outcome send(String recipient, List<String> tokens, Map<String, String> data, Duration ttl) {
        int sent = 0;
        int failed = 0;
        List<String> dead = new ArrayList<>();
        for (String token : tokens) {
            switch (fcm.send(token, data, ttl)) {
                case SENT -> sent++;
                case TOKEN_INVALID -> dead.add(token);
                case FAILED -> failed++;
            }
        }
        if (!dead.isEmpty()) {
            firestore.removeFromArray("tokens", dead, "users", recipient, "private", "devices");
        }
        return new Outcome(sent, failed, dead.size());
    }
}
