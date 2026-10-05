package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** FCM HTTP v1: one data-only, high-priority message to one registration token, optionally with a time to live. */
@Component
public class FcmClient {

    /** What happened to one token. */
    public enum Result {
        SENT,
        /** The token will never work again (app uninstalled, token rotated, other project): remove it. */
        TOKEN_INVALID,
        /** FCM or the network failed (quota, 5xx, ...): keep the token. */
        FAILED
    }

    private static final Logger logger = LoggerFactory.getLogger(FcmClient.class);
    private static final ObjectMapper ERROR_READER = new ObjectMapper();

    private final GoogleApiClient api;
    private final ChatFirebaseCredentials credentials;
    private final ObjectMapper objectMapper;

    public FcmClient(GoogleApiClient api, ChatFirebaseCredentials credentials, ObjectMapper objectMapper) {
        this.api = api;
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code ttl}: how long FCM keeps the message for a device that is offline before dropping it (a call must not
     * ring minutes later); null leaves FCM's default of four weeks.
     */
    public Result send(String token, Map<String, String> data, Duration ttl) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode message = body.putObject("message");
        message.put("token", token);
        ObjectNode dataNode = message.putObject("data");
        // FCM data values must be strings; null is rejected
        data.forEach((key, value) -> dataNode.put(key, value == null ? "" : value));
        // data-only + HIGH so the app's FirebaseMessagingService runs (and builds the notification) even in Doze
        ObjectNode android = message.putObject("android").put("priority", "HIGH");
        if (ttl != null) {
            // a protobuf Duration in JSON: whole seconds followed by "s"
            android.put("ttl", Math.max(0, ttl.getSeconds()) + "s");
        }

        String url = "https://fcm.googleapis.com/v1/projects/"
                + URLEncoder.encode(credentials.projectId(), StandardCharsets.UTF_8) + "/messages:send";
        ChatHttpClient.Response response;
        try {
            response = api.send("POST", url, body.toString());
        } catch (ChatException e) {
            return Result.FAILED;
        }
        if (response.isSuccess()) {
            return Result.SENT;
        }
        Result result = classifyError(response.status(), response.body());
        if (result == Result.FAILED) {
            logger.warn("FCM send failed: HTTP {} {}", response.status(), FirestoreRestClient.errorSummary(response.body()));
        }
        return result;
    }

    /**
     * 404 / UNREGISTERED: the app instance is gone. 400 INVALID_ARGUMENT about the token: it was never valid. 403
     * SENDER_ID_MISMATCH: the token belongs to another Firebase project. Other 400s (e.g. a payload FCM rejects)
     * must not be blamed on the token, otherwise one bug would wipe every user's tokens.
     */
    static Result classifyError(int status, String body) {
        JsonNode error = parse(body).path("error");
        String errorStatus = error.path("status").asText("");
        String message = error.path("message").asText("").toLowerCase(Locale.ROOT);
        String fcmErrorCode = "";
        boolean tokenField = false;
        for (JsonNode detail : error.path("details")) {
            if (detail.hasNonNull("errorCode")) {
                fcmErrorCode = detail.get("errorCode").asText("");
            }
            for (JsonNode violation : detail.path("fieldViolations")) {
                if ("message.token".equals(violation.path("field").asText(""))) {
                    tokenField = true;
                }
            }
        }
        if (status == 404 || "UNREGISTERED".equals(fcmErrorCode)) {
            return Result.TOKEN_INVALID;
        }
        if (status == 400 && ("INVALID_ARGUMENT".equals(errorStatus) || "INVALID_ARGUMENT".equals(fcmErrorCode))
                && (tokenField || message.contains("registration token"))) {
            return Result.TOKEN_INVALID;
        }
        if ("SENDER_ID_MISMATCH".equals(fcmErrorCode)) {
            return Result.TOKEN_INVALID;
        }
        return Result.FAILED;
    }

    private static JsonNode parse(String body) {
        try {
            JsonNode node = body == null || body.isBlank() ? null : ERROR_READER.readTree(body);
            return node == null ? ERROR_READER.createObjectNode() : node;
        } catch (IOException e) {
            return ERROR_READER.createObjectNode();
        }
    }
}
