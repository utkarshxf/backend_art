package com.basic.JWTSecurity.chat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * The Google service account chat uses to mint Firebase custom tokens and to call Firestore and FCM, read once at
 * startup from {@code firebase.service-account-b64} (env FIREBASE_SERVICE_ACCOUNT_B64: the base64 of the key JSON
 * downloaded from the Firebase console). A missing or broken value only disables chat: /chat/** then answers 503 and
 * the rest of the backend keeps working. Nothing from the key is ever logged.
 */
@Component
public class ChatFirebaseCredentials {

    private static final Logger logger = LoggerFactory.getLogger(ChatFirebaseCredentials.class);

    /** The parts of the key JSON chat needs. */
    public record ServiceAccount(String projectId, String clientEmail, String privateKeyId, PrivateKey privateKey) {
        @Override
        public String toString() {
            // the default record toString would include the private key
            return "ServiceAccount[" + clientEmail + "]";
        }
    }

    private final ServiceAccount serviceAccount;
    private final String projectId;

    public ChatFirebaseCredentials(@Value("${firebase.project-id:}") String projectId,
                                   @Value("${firebase.service-account-b64:}") String serviceAccountB64,
                                   ObjectMapper objectMapper) {
        ServiceAccount parsed = null;
        if (serviceAccountB64 == null || serviceAccountB64.isBlank()) {
            logger.info("Chat is disabled: FIREBASE_SERVICE_ACCOUNT_B64 is not set");
        } else {
            try {
                parsed = parse(serviceAccountB64, objectMapper);
            } catch (IllegalArgumentException e) {
                // the message is one of parse()'s own descriptions, never key material
                logger.error("Chat is disabled: FIREBASE_SERVICE_ACCOUNT_B64 is not a usable service-account key: {}",
                        e.getMessage());
            }
        }
        String project = projectId == null ? "" : projectId.trim();
        if (project.isEmpty() && parsed != null && parsed.projectId() != null) {
            project = parsed.projectId();
        }
        if (parsed != null && project.isEmpty()) {
            logger.error("Chat is disabled: no Firebase project id (set FIREBASE_PROJECT_ID)");
            parsed = null;
        }
        if (parsed != null) {
            if (parsed.projectId() != null && !parsed.projectId().equals(project)) {
                logger.warn("Chat service account belongs to project {} but firebase.project-id is {}; Firestore and "
                        + "FCM calls go to {} and need the account to have access there", parsed.projectId(), project, project);
            }
            logger.info("Chat is enabled for Firebase project {}", project);
        }
        this.serviceAccount = parsed;
        this.projectId = project;
    }

    /** Empty when chat is not configured. */
    public Optional<ServiceAccount> serviceAccount() {
        return Optional.ofNullable(serviceAccount);
    }

    public boolean isConfigured() {
        return serviceAccount != null;
    }

    /** The Firebase project Firestore and FCM requests go to. */
    public String projectId() {
        return projectId;
    }

    /**
     * Parses base64 of the key JSON (whitespace/line breaks and URL-safe base64 tolerated; raw JSON is accepted too).
     * Throws {@link IllegalArgumentException} with a description that never contains key material.
     */
    static ServiceAccount parse(String value, ObjectMapper objectMapper) {
        String trimmed = value.trim();
        String json;
        if (trimmed.startsWith("{")) {
            json = trimmed;
        } else {
            String compact = trimmed.replaceAll("\\s", "");
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(compact);
            } catch (IllegalArgumentException e) {
                try {
                    bytes = Base64.getUrlDecoder().decode(compact);
                } catch (IllegalArgumentException urlSafe) {
                    throw new IllegalArgumentException("the value is not valid base64");
                }
            }
            json = new String(bytes, StandardCharsets.UTF_8);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            // Jackson messages can quote the input, which holds the private key
            throw new IllegalArgumentException("the decoded value is not JSON");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("the decoded value is not a JSON object");
        }
        String type = text(node, "type");
        if (type != null && !"service_account".equals(type)) {
            throw new IllegalArgumentException("type is \"" + type + "\", expected \"service_account\"");
        }
        String clientEmail = text(node, "client_email");
        if (clientEmail == null) {
            throw new IllegalArgumentException("client_email is missing");
        }
        String privateKeyPem = text(node, "private_key");
        if (privateKeyPem == null) {
            throw new IllegalArgumentException("private_key is missing");
        }
        return new ServiceAccount(text(node, "project_id"), clientEmail, text(node, "private_key_id"),
                parsePrivateKey(privateKeyPem));
    }

    private static PrivateKey parsePrivateKey(String pem) {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                // a key JSON that was escaped twice carries literal "\n" sequences
                .replace("\\n", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalArgumentException("private_key is not a PKCS#8 RSA private key");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
