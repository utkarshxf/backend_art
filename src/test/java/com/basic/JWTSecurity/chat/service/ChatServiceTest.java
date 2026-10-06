package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ChatService with Google's HTTP APIs (OAuth, Firestore REST, FCM) faked behind a mocked ChatHttpClient
class ChatServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROJECT = "test-project";
    private static final String CLIENT_EMAIL = "chat-backend@test-project.iam.gserviceaccount.com";
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/test-project/messages:send";
    private static final String COMMIT_URL =
            "https://firestore.googleapis.com/v1/projects/test-project/databases/(default)/documents:commit";

    private static KeyPair keyPair;
    private static String serviceAccountJson;
    private static String serviceAccountB64;

    private TestClock clock;
    private FakeGoogle google;
    private ChatHttpClient http;
    private ChatService service;

    @BeforeAll
    static void createServiceAccount() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Map<String, String> json = new LinkedHashMap<>();
        json.put("type", "service_account");
        json.put("project_id", PROJECT);
        json.put("private_key_id", "kid-1");
        json.put("private_key", pem);
        json.put("client_email", CLIENT_EMAIL);
        json.put("token_uri", "https://oauth2.googleapis.com/token");
        serviceAccountJson = MAPPER.writeValueAsString(json);
        serviceAccountB64 = Base64.getEncoder().encodeToString(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() throws IOException {
        clock = new TestClock(NOW);
        google = new FakeGoogle();
        http = mock(ChatHttpClient.class);
        when(http.send(any())).thenAnswer(invocation -> google.handle(invocation.getArgument(0)));
        service = newService(new ChatFirebaseCredentials(PROJECT, serviceAccountB64, MAPPER));
    }

    private ChatService newService(ChatFirebaseCredentials credentials) {
        GoogleAccessTokenProvider tokens = new GoogleAccessTokenProvider(credentials, http, MAPPER, clock);
        GoogleApiClient api = new GoogleApiClient(tokens, http);
        FirestoreRestClient firestore = new FirestoreRestClient(api, credentials, MAPPER);
        return new ChatService(credentials, firestore,
                new DevicePusher(firestore, new FcmClient(api, credentials, MAPPER)), clock);
    }

    // ---- POST /chat/token

    @Test
    void customTokenIsSignedWithTheServiceAccountForFirebaseAuth() throws Exception {
        String token = service.mintCustomToken("alice");

        Jws<Claims> jws = Jwts.parser().verifyWith(publicKeyDerivedFromTheServiceAccount())
                .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token);
        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals("JWT", jws.getHeader().getType());
        Claims claims = jws.getPayload();
        assertEquals(CLIENT_EMAIL, claims.getIssuer());
        assertEquals(CLIENT_EMAIL, claims.getSubject());
        assertEquals("alice", claims.get("uid", String.class));
        assertEquals(NOW, claims.getIssuedAt().toInstant());
        assertEquals(NOW.plusSeconds(3600), claims.getExpiration().toInstant());
        // Firebase expects aud as a plain string, as the Admin SDKs write it
        JsonNode payload = MAPPER.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertTrue(payload.get("aud").isTextual());
        assertEquals(ChatService.FIREBASE_AUDIENCE, payload.get("aud").asText());
        // minted locally: no call to Google
        verifyNoInteractions(http);
    }

    @Test
    void customTokenDoesNotVerifyWithAnotherKey() throws Exception {
        String token = service.mintCustomToken("alice");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PublicKey otherKey = generator.generateKeyPair().getPublic();
        assertThrows(SignatureException.class, () -> Jwts.parser().verifyWith(otherKey)
                .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token));
    }

    @Test
    void customTokenRejectsUnusableUids() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.mintCustomToken(""));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.mintCustomToken("a".repeat(129)));
    }

    // ---- not configured

    @Test
    void unconfiguredChatAnswers503AndCallsNothing() {
        for (String value : new String[]{"", "   ", "not base64 !!", Base64.getEncoder().encodeToString("nope".getBytes()),
                Base64.getEncoder().encodeToString("{\"type\":\"service_account\",\"client_email\":\"x@y\"}".getBytes()),
                Base64.getEncoder().encodeToString("{\"type\":\"authorized_user\"}".getBytes())}) {
            ChatFirebaseCredentials credentials = new ChatFirebaseCredentials(PROJECT, value, MAPPER);
            assertFalse(credentials.isConfigured());
            ChatService unconfigured = newService(credentials);

            ChatException token = assertThrows(ChatException.class, () -> unconfigured.mintCustomToken("alice"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, token.getStatus());
            assertEquals("Chat is not configured yet", token.getMessage());
            ChatException notify = assertThrows(ChatException.class,
                    () -> unconfigured.notifyRecipient("alice", "alice__bob", "m1"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, notify.getStatus());
        }
        verifyNoInteractions(http);
    }

    @Test
    void serviceAccountAcceptsWrappedBase64AndRawJson() {
        String wrapped = Base64.getMimeEncoder().encodeToString(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        assertTrue(wrapped.contains("\r\n"));
        assertTrue(new ChatFirebaseCredentials(PROJECT, wrapped, MAPPER).isConfigured());
        assertTrue(new ChatFirebaseCredentials(PROJECT, serviceAccountJson, MAPPER).isConfigured());
        // the project comes from the key when firebase.project-id is empty
        assertEquals(PROJECT, new ChatFirebaseCredentials("", serviceAccountB64, MAPPER).projectId());
        // never print the key
        String printed = new ChatFirebaseCredentials(PROJECT, serviceAccountB64, MAPPER).serviceAccount().orElseThrow().toString();
        assertFalse(printed.contains("PRIVATE") || printed.length() > 200, printed);
    }

    // ---- POST /chat/notify

    @Test
    void pushesADataOnlyHighPriorityMessageToEveryDeviceOfThePeer() throws Exception {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "  Hello Bob  ", NOW.minusSeconds(5));
        google.devices("bob", "token-1", "token-2", "token-1");
        google.profile("alice", "Alice A", "https://img/alice.png");

        assertEquals(2, service.notifyRecipient("alice", "alice__bob", "m1"));

        List<ChatHttpClient.Request> pushes = google.requestsTo(FCM_URL);
        assertEquals(2, pushes.size());
        List<String> tokens = new ArrayList<>();
        for (ChatHttpClient.Request push : pushes) {
            assertEquals("POST", push.method());
            assertEquals("Bearer " + FakeGoogle.ACCESS_TOKEN, push.headers().get("Authorization"));
            JsonNode message = MAPPER.readTree(push.body()).get("message");
            tokens.add(message.get("token").asText());
            assertEquals("HIGH", message.at("/android/priority").asText());
            assertFalse(message.has("notification"), "must be data-only so the app builds the notification");
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("type", "chat_message");
            expected.put("conversationId", "alice__bob");
            expected.put("messageId", "m1");
            expected.put("sender", "alice");
            expected.put("senderName", "Alice A");
            expected.put("senderAvatar", "https://img/alice.png");
            expected.put("preview", "Hello Bob");
            assertEquals(expected, MAPPER.convertValue(message.get("data"), Map.class));
        }
        assertEquals(List.of("token-1", "token-2"), tokens);
        assertTrue(google.requestsTo(COMMIT_URL).isEmpty(), "no token was dead");
    }

    @Test
    void oauthAssertionIsASignedJwtBearerGrantForFirestoreAndFcm() throws Exception {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "hi", NOW);
        google.devices("bob", "token-1");

        service.notifyRecipient("alice", "alice__bob", "m1");

        List<ChatHttpClient.Request> grants = google.requestsTo(GoogleAccessTokenProvider.TOKEN_URL);
        assertEquals(1, grants.size());
        assertEquals("application/x-www-form-urlencoded", grants.get(0).headers().get("Content-Type"));
        Map<String, String> form = parseForm(grants.get(0).body());
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form.get("grant_type"));
        Jws<Claims> assertion = Jwts.parser().verifyWith(publicKeyDerivedFromTheServiceAccount())
                .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(form.get("assertion"));
        assertEquals("kid-1", assertion.getHeader().getKeyId());
        assertEquals(CLIENT_EMAIL, assertion.getPayload().getIssuer());
        assertEquals(GoogleAccessTokenProvider.SCOPES, assertion.getPayload().get("scope", String.class));
        assertTrue(assertion.getPayload().getAudience().contains(GoogleAccessTokenProvider.TOKEN_URL));
        // every Google call carries the access token
        for (ChatHttpClient.Request request : google.requests) {
            if (!request.url().equals(GoogleAccessTokenProvider.TOKEN_URL)) {
                assertEquals("Bearer " + FakeGoogle.ACCESS_TOKEN, request.headers().get("Authorization"), request.url());
            }
        }
    }

    @Test
    void senderMismatchIs403() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "bob", "text", "written by bob", NOW);
        google.devices("bob", "token-1");

        assertStatus(HttpStatus.FORBIDDEN, () -> service.notifyRecipient("alice", "alice__bob", "m1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void nonMemberIs403() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "hi", NOW);
        google.devices("bob", "token-1");

        // not even a candidate from the id: refused without asking Firestore
        assertStatus(HttpStatus.FORBIDDEN, () -> service.notifyRecipient("carol", "alice__bob", "m1"));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.notifyRecipient("ali", "alice__bob", "m1"));
        assertTrue(google.requests.isEmpty());

        // the id looks right but the stored members are someone else
        google.put("conversations/carol__dave", conversationFields(List.of("mallory", "dave"), Map.of()));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.notifyRecipient("carol", "carol__dave", "m1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void mutedRecipientGetsNoPush() {
        google.conversation("alice", "bob", Map.of("bob", true));
        google.message("alice__bob", "m1", "alice", "text", "hi", NOW);
        google.devices("bob", "token-1");

        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "m1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
        assertTrue(google.requestsFor("users/bob/private/devices").isEmpty(), "no need to read the devices");
    }

    @Test
    void mutedBySenderOnlyStillPushes() {
        google.conversation("alice", "bob", Map.of("alice", true));
        google.message("alice__bob", "m1", "alice", "text", "hi", NOW);
        google.devices("bob", "token-1");

        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m1"));
    }

    @Test
    void deadTokensArePrunedFromTheDevicesDocument() throws Exception {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "image", "", NOW);
        google.devices("bob", "good", "gone", "bogus", "flaky");
        google.fcm("gone", 404, "{\"error\":{\"code\":404,\"message\":\"Requested entity was not found.\",\"status\":\"NOT_FOUND\","
                + "\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"UNREGISTERED\"}]}}");
        google.fcm("bogus", 400, "{\"error\":{\"code\":400,\"message\":\"The registration token is not a valid FCM registration token\","
                + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\","
                + "\"errorCode\":\"INVALID_ARGUMENT\"}]}}");
        google.fcm("flaky", 503, "{\"error\":{\"code\":503,\"message\":\"The service is currently unavailable.\",\"status\":\"UNAVAILABLE\"}}");

        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m1"));

        List<ChatHttpClient.Request> commits = google.requestsTo(COMMIT_URL);
        assertEquals(1, commits.size());
        JsonNode write = MAPPER.readTree(commits.get(0).body()).at("/writes/0");
        assertEquals("projects/test-project/databases/(default)/documents/users/bob/private/devices",
                write.at("/transform/document").asText());
        assertEquals("tokens", write.at("/transform/fieldTransforms/0/fieldPath").asText());
        List<String> removed = new ArrayList<>();
        write.at("/transform/fieldTransforms/0/removeAllFromArray/values").forEach(v -> removed.add(v.get("stringValue").asText()));
        assertEquals(List.of("gone", "bogus"), removed, "the flaky token is kept");
        assertTrue(write.at("/currentDocument/exists").asBoolean(), "never recreate a deleted devices doc");
        // the preview of a photo
        assertEquals("Sent a photo", MAPPER.readTree(google.requestsTo(FCM_URL).get(0).body()).at("/message/data/preview").asText());
    }

    @Test
    void aPayloadErrorIsNotBlamedOnTheTokens() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "hi", NOW);
        google.devices("bob", "token-1");
        google.fcm("token-1", 400, "{\"error\":{\"code\":400,\"message\":\"Invalid value at 'message.data[0].value'\","
                + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.BadRequest\","
                + "\"fieldViolations\":[{\"field\":\"message.data[0].value\",\"description\":\"Invalid value\"}]}]}}");

        // nothing delivered: 502 so the app may retry, and the token stays
        assertStatus(HttpStatus.BAD_GATEWAY, () -> service.notifyRecipient("alice", "alice__bob", "m1"));
        assertTrue(google.requestsTo(COMMIT_URL).isEmpty());

        // a failed attempt does not count as pushed
        google.fcm("token-1", 200, "{\"name\":\"projects/test-project/messages/1\"}");
        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m1"));
    }

    @Test
    void eachMessageIsPushedOnceAndTheAccessTokenIsReused() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "one", NOW);
        google.message("alice__bob", "m2", "alice", "text", "two", NOW);
        google.devices("bob", "token-1");

        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m1"));
        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "m1"));
        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m2"));

        assertEquals(2, google.requestsTo(FCM_URL).size());
        assertEquals(1, google.requestsTo(GoogleAccessTokenProvider.TOKEN_URL).size());
    }

    @Test
    void accessTokenIsRefreshedBeforeItExpiresAndAfterA401() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "text", "one", NOW);
        google.devices("bob", "token-1");
        service.notifyRecipient("alice", "alice__bob", "m1");
        assertEquals(1, google.requestsTo(GoogleAccessTokenProvider.TOKEN_URL).size());

        // expires_in is 3599 s: at 55 minutes the token is within 5 minutes of expiry
        clock.now = NOW.plus(Duration.ofMinutes(55));
        google.message("alice__bob", "m2", "alice", "text", "two", clock.now);
        service.notifyRecipient("alice", "alice__bob", "m2");
        assertEquals(2, google.requestsTo(GoogleAccessTokenProvider.TOKEN_URL).size());

        // revoked early: Firestore answers 401 once, the call is retried with a new token
        google.rejectNextAuthorizedCall = true;
        google.message("alice__bob", "m3", "alice", "text", "three", clock.now);
        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m3"));
        assertEquals(3, google.requestsTo(GoogleAccessTokenProvider.TOKEN_URL).size());
    }

    @Test
    void failedOauthIs502() {
        google.conversation("alice", "bob", Map.of());
        google.oauthStatus = 400;
        assertStatus(HttpStatus.BAD_GATEWAY, () -> service.notifyRecipient("alice", "alice__bob", "m1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void unsentOrOldMessagesAreNotPushed() {
        google.conversation("alice", "bob", Map.of());
        google.devices("bob", "token-1");
        google.message("alice__bob", "old", "alice", "text", "hi", NOW.minus(Duration.ofMinutes(16)));
        Map<String, JsonNode> unsent = messageFields("alice", "text", "", NOW);
        unsent.put("unsent", bool(true));
        google.put("conversations/alice__bob/messages/unsent", unsent);

        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "old"));
        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "unsent"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void aCallsRowInTheThreadIsNotPushedAsAMessage() {
        google.conversation("alice", "bob", Map.of());
        google.devices("bob", "token-1");
        // written by the backend when the call ended; the caller cannot turn it into a message notification
        google.message("alice__bob", "call-1", "alice", "call", "Missed video call", NOW);

        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "call-1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void recipientWithoutDevicesGetsNothing() {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "like", "", NOW);
        assertEquals(0, service.notifyRecipient("alice", "alice__bob", "m1"));
        assertTrue(google.requestsTo(FCM_URL).isEmpty());
    }

    @Test
    void senderWithoutProfileIsNamedByUsername() throws Exception {
        google.conversation("alice", "bob", Map.of());
        google.message("alice__bob", "m1", "alice", "like", "", NOW);
        google.devices("bob", "token-1");

        assertEquals(1, service.notifyRecipient("alice", "alice__bob", "m1"));
        JsonNode data = MAPPER.readTree(google.requestsTo(FCM_URL).get(0).body()).at("/message/data");
        assertEquals("alice", data.get("senderName").asText());
        assertEquals("", data.get("senderAvatar").asText());
        assertEquals("❤️", data.get("preview").asText());
    }

    @Test
    void missingConversationOrMessageIs404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.notifyRecipient("alice", "alice__bob", "m1"));
        google.conversation("alice", "bob", Map.of());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.notifyRecipient("alice", "alice__bob", "m1"));
    }

    @Test
    void unsafeIdsAre400WithoutCallingGoogle() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", null, "m1"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "", "m1"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "alice__bob/messages", "m1"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "alice__bob", "../x"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "alice__bob", ".."));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "__alice__", "m1"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.notifyRecipient("alice", "alice__bob", " "));
        assertTrue(google.requests.isEmpty());
    }

    // ---- helpers of the service

    @Test
    void previewMatchesTheAppAndNeverSplitsAnEmoji() {
        assertEquals("hi", ChatService.preview(Map.of("type", "text", "text", "  hi \n")));
        assertEquals("Sent a photo", ChatService.preview(Map.of("type", "image", "text", "caption")));
        assertEquals("Shared a post", ChatService.preview(Map.of("type", "artwork")));
        assertEquals("Shared a profile", ChatService.preview(Map.of("type", "profile")));
        assertEquals("❤️", ChatService.preview(Map.of("type", "like")));
        // a sticker is an image message with the sticker flag
        assertEquals("Sent a sticker", ChatService.preview(Map.of("type", "image", "sticker", true)));
        assertEquals("Sent a photo", ChatService.preview(Map.of("type", "image", "sticker", false)));
        assertEquals("Sent a message", ChatService.preview(Map.of("type", "poll")));
        String emojis = "😀".repeat(130);
        String cut = ChatService.preview(Map.of("type", "text", "text", emojis));
        assertEquals(120, cut.codePointCount(0, cut.length()));
        assertEquals("😀".repeat(120), cut);
        assertEquals("a".repeat(120), ChatService.preview(Map.of("type", "text", "text", "a".repeat(121))));
    }

    @Test
    void firestoreValuesAreDecoded() throws Exception {
        JsonNode fields = MAPPER.readTree("{"
                + "\"s\":{\"stringValue\":\"x\"},"
                + "\"b\":{\"booleanValue\":true},"
                + "\"i\":{\"integerValue\":\"42\"},"
                + "\"d\":{\"doubleValue\":1.5},"
                + "\"t\":{\"timestampValue\":\"2026-09-28T12:00:00.123456Z\"},"
                + "\"n\":{\"nullValue\":null},"
                + "\"emptyMap\":{\"mapValue\":{}},"
                + "\"emptyArray\":{\"arrayValue\":{}},"
                + "\"m\":{\"mapValue\":{\"fields\":{\"bob\":{\"booleanValue\":true},\"inner\":{\"mapValue\":{\"fields\":{\"k\":{\"stringValue\":\"v\"}}}}}}},"
                + "\"a\":{\"arrayValue\":{\"values\":[{\"stringValue\":\"t1\"},{\"integerValue\":\"7\"}]}}"
                + "}");
        Map<String, Object> decoded = FirestoreRestClient.decodeFields(fields);
        assertEquals("x", decoded.get("s"));
        assertEquals(true, decoded.get("b"));
        assertEquals(42L, decoded.get("i"));
        assertEquals(1.5, decoded.get("d"));
        assertEquals(Instant.parse("2026-09-28T12:00:00.123456Z"), decoded.get("t"));
        assertTrue(decoded.containsKey("n"));
        assertNull(decoded.get("n"));
        assertEquals(Map.of(), decoded.get("emptyMap"));
        assertEquals(List.of(), decoded.get("emptyArray"));
        assertEquals(Map.of("bob", true, "inner", Map.of("k", "v")), decoded.get("m"));
        assertEquals(List.of("t1", 7L), decoded.get("a"));
        assertEquals(Map.of(), FirestoreRestClient.decodeFields(MAPPER.readTree("{}").path("fields")));
    }

    @Test
    void fcmErrorsAreClassified() {
        assertEquals(FcmClient.Result.TOKEN_INVALID, FcmClient.classifyError(404, "{\"error\":{\"status\":\"NOT_FOUND\"}}"));
        assertEquals(FcmClient.Result.TOKEN_INVALID, FcmClient.classifyError(400,
                "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"fieldViolations\":[{\"field\":\"message.token\"}]}]}}"));
        assertEquals(FcmClient.Result.TOKEN_INVALID, FcmClient.classifyError(403,
                "{\"error\":{\"status\":\"PERMISSION_DENIED\",\"details\":[{\"errorCode\":\"SENDER_ID_MISMATCH\"}]}}"));
        assertEquals(FcmClient.Result.FAILED, FcmClient.classifyError(400,
                "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"Invalid JSON payload received.\"}}"));
        assertEquals(FcmClient.Result.FAILED, FcmClient.classifyError(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        assertEquals(FcmClient.Result.FAILED, FcmClient.classifyError(500, "not json"));
        assertEquals(FcmClient.Result.FAILED, FcmClient.classifyError(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));
    }

    // ---- fixtures

    private static PublicKey publicKeyDerivedFromTheServiceAccount() throws Exception {
        String pem = MAPPER.readTree(serviceAccountJson).get("private_key").asText();
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        return rsa.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
    }

    private static void assertStatus(HttpStatus expected, Executable call) {
        ChatException e = assertThrows(ChatException.class, call::execute);
        assertEquals(expected, e.getStatus(), e.getMessage());
    }

    @FunctionalInterface
    private interface Executable {
        void execute() throws Throwable;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return form;
    }

    private static JsonNode str(String value) {
        return MAPPER.createObjectNode().put("stringValue", value);
    }

    private static JsonNode bool(boolean value) {
        return MAPPER.createObjectNode().put("booleanValue", value);
    }

    private static JsonNode timestamp(Instant value) {
        return MAPPER.createObjectNode().put("timestampValue", value.toString());
    }

    private static JsonNode array(List<String> values) {
        ObjectNode node = MAPPER.createObjectNode();
        ArrayNode array = node.putObject("arrayValue").putArray("values");
        values.forEach(value -> array.add(str(value)));
        return node;
    }

    private static JsonNode map(Map<String, JsonNode> fields) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode mapValue = node.putObject("mapValue");
        if (!fields.isEmpty()) {
            ObjectNode inner = mapValue.putObject("fields");
            fields.forEach(inner::set);
        }
        return node;
    }

    private static Map<String, JsonNode> conversationFields(List<String> usernames, Map<String, Boolean> muted) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put("usernames", array(usernames));
        fields.put("createdAt", timestamp(NOW.minus(Duration.ofDays(1))));
        fields.put("updatedAt", timestamp(NOW));
        fields.put("lastMessage", MAPPER.createObjectNode().putNull("nullValue"));
        fields.put("unread", map(Map.of()));
        fields.put("lastRead", map(Map.of()));
        fields.put("typing", map(Map.of()));
        Map<String, JsonNode> mutedFields = new LinkedHashMap<>();
        muted.forEach((user, value) -> mutedFields.put(user, bool(value)));
        fields.put("muted", map(mutedFields));
        fields.put("markedUnread", map(Map.of()));
        fields.put("clearedAt", map(Map.of()));
        return fields;
    }

    private static Map<String, JsonNode> messageFields(String sender, String type, String text, Instant createdAt) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put("sender", str(sender));
        fields.put("type", str(type));
        fields.put("text", str(text));
        fields.put("imageUrl", MAPPER.createObjectNode().putNull("nullValue"));
        fields.put("replyTo", MAPPER.createObjectNode().putNull("nullValue"));
        fields.put("reactions", map(Map.of()));
        fields.put("createdAt", timestamp(createdAt));
        fields.put("unsent", bool(false));
        return fields;
    }

    /** Google's OAuth, Firestore REST and FCM endpoints, answering from in-memory documents. */
    private static final class FakeGoogle {
        static final String ACCESS_TOKEN = "ya29.test-access-token";
        private static final String DOCUMENTS =
                "https://firestore.googleapis.com/v1/projects/test-project/databases/(default)/documents/";

        final List<ChatHttpClient.Request> requests = new ArrayList<>();
        final Map<String, String> documents = new HashMap<>();
        final Map<String, ChatHttpClient.Response> fcmResponses = new HashMap<>();
        int oauthStatus = 200;
        boolean rejectNextAuthorizedCall;

        void put(String path, Map<String, JsonNode> fields) {
            ObjectNode document = MAPPER.createObjectNode();
            document.put("name", "projects/test-project/databases/(default)/documents/" + path);
            ObjectNode fieldsNode = document.putObject("fields");
            fields.forEach(fieldsNode::set);
            documents.put(path, document.toString());
        }

        void conversation(String first, String second, Map<String, Boolean> muted) {
            put("conversations/" + first + "__" + second, conversationFields(List.of(first, second), muted));
        }

        void message(String conversationId, String messageId, String sender, String type, String text, Instant createdAt) {
            put("conversations/" + conversationId + "/messages/" + messageId, messageFields(sender, type, text, createdAt));
        }

        void devices(String username, String... tokens) {
            put("users/" + username + "/private/devices", Map.of("tokens", array(List.of(tokens))));
        }

        void profile(String username, String name, String avatar) {
            Map<String, JsonNode> fields = new LinkedHashMap<>();
            fields.put("username", str(username));
            fields.put("name", str(name));
            fields.put("avatar", str(avatar));
            fields.put("lastActive", timestamp(NOW));
            put("users/" + username, fields);
        }

        void fcm(String token, int status, String body) {
            fcmResponses.put(token, new ChatHttpClient.Response(status, body));
        }

        List<ChatHttpClient.Request> requestsTo(String url) {
            return requests.stream().filter(request -> request.url().equals(url)).toList();
        }

        List<ChatHttpClient.Request> requestsFor(String documentPath) {
            return requestsTo(DOCUMENTS + documentPath);
        }

        ChatHttpClient.Response handle(ChatHttpClient.Request request) throws IOException {
            requests.add(request);
            String url = request.url();
            if (url.equals(GoogleAccessTokenProvider.TOKEN_URL)) {
                return oauthStatus == 200
                        ? new ChatHttpClient.Response(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":3599,\"token_type\":\"Bearer\"}")
                        : new ChatHttpClient.Response(oauthStatus, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid JWT Signature.\"}");
            }
            if (!("Bearer " + ACCESS_TOKEN).equals(request.headers().get("Authorization"))) {
                return new ChatHttpClient.Response(401, "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}");
            }
            if (rejectNextAuthorizedCall) {
                rejectNextAuthorizedCall = false;
                return new ChatHttpClient.Response(401, "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}");
            }
            if (url.equals(FCM_URL)) {
                String token = MAPPER.readTree(request.body()).at("/message/token").asText();
                return fcmResponses.getOrDefault(token, new ChatHttpClient.Response(200, "{\"name\":\"projects/test-project/messages/1\"}"));
            }
            if (url.equals(COMMIT_URL)) {
                return new ChatHttpClient.Response(200, "{\"writeResults\":[{}],\"commitTime\":\"" + NOW + "\"}");
            }
            if (url.startsWith(DOCUMENTS) && request.method().equals("GET")) {
                List<String> segments = new ArrayList<>();
                for (String segment : url.substring(DOCUMENTS.length()).split("/")) {
                    segments.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
                }
                String document = documents.get(String.join("/", segments));
                return document == null
                        ? new ChatHttpClient.Response(404, "{\"error\":{\"code\":404,\"message\":\"Document not found\",\"status\":\"NOT_FOUND\"}}")
                        : new ChatHttpClient.Response(200, document);
            }
            throw new AssertionError("unexpected request " + request);
        }
    }

    private static final class TestClock extends Clock {
        Instant now;

        TestClock(Instant now) {
            this.now = now;
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
