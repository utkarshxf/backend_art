package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Google's OAuth, Firestore REST and FCM endpoints answering from memory, wired to the real clients
 * ({@link #firestore()}, {@link #pusher()}). Unlike a canned mock it behaves like Firestore where it matters for
 * calls: documents have versions, and a commit is atomic and honours its preconditions, update masks and
 * increments, so races between two phones can be played out.
 */
public final class FakeFirebase implements ChatHttpClient {

    public static final String PROJECT = "test-project";

    private static final String ACCESS_TOKEN = "ya29.fake-access-token";
    private static final String NAME_PREFIX = "projects/" + PROJECT + "/databases/(default)/documents/";
    private static final String DOCUMENTS_URL = "https://firestore.googleapis.com/v1/" + NAME_PREFIX;
    private static final String COMMIT_URL =
            "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents:commit";
    private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/" + PROJECT + "/messages:send";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SERVICE_ACCOUNT = serviceAccountJson();

    /** One message FCM accepted: {@code ttl} is e.g. "45s", null when the request set none. */
    public record Push(String token, Map<String, String> data, String ttl, String priority) {
    }

    private final ChatFirebaseCredentials credentials = new ChatFirebaseCredentials(PROJECT, SERVICE_ACCOUNT, MAPPER);
    private final FirestoreRestClient firestore;
    private final DevicePusher pusher;

    /** document path ("calls/abc") -> its fields as typed Firestore JSON */
    private final Map<String, ObjectNode> documents = new LinkedHashMap<>();
    private final Map<String, Integer> versions = new HashMap<>();
    private final List<Push> pushes = new ArrayList<>();
    private final Set<String> deadTokens = new HashSet<>();
    private Runnable beforeNextCommit;
    private boolean unreachable;
    private int commits;

    public FakeFirebase() {
        GoogleApiClient api = new GoogleApiClient(new GoogleAccessTokenProvider(credentials, this, MAPPER), this);
        firestore = new FirestoreRestClient(api, credentials, MAPPER);
        pusher = new DevicePusher(firestore, new FcmClient(api, credentials, MAPPER));
    }

    public ChatFirebaseCredentials credentials() {
        return credentials;
    }

    public FirestoreRestClient firestore() {
        return firestore;
    }

    public DevicePusher pusher() {
        return pusher;
    }

    // ------------------------------------------------------------------ seeding and inspecting

    /** Writes a document; values as for {@link FirestoreRestClient#encodeValue}. */
    public void put(String path, Map<String, ?> fields) {
        documents.put(path, FirestoreRestClient.encodeFields(fields));
        versions.merge(path, 1, Integer::sum);
    }

    /** The chat profile every reachable user has. */
    public void user(String username, String name, String avatar) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", username);
        fields.put("name", name);
        fields.put("avatar", avatar);
        put("users/" + username, fields);
    }

    public void devices(String username, String... tokens) {
        put("users/" + username + "/private/devices", Map.of("tokens", List.of(tokens)));
    }

    /** An existing conversation of {@code first} and {@code second} (sorted), as the app creates it. */
    public void conversation(String first, String second, Map<String, Long> unread, Map<String, Boolean> muted) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("usernames", List.of(first, second));
        fields.put("lastMessage", null);
        fields.put("unread", unread);
        fields.put("lastRead", Map.of());
        fields.put("typing", Map.of());
        fields.put("muted", muted);
        fields.put("markedUnread", Map.of());
        fields.put("clearedAt", Map.of());
        put("conversations/" + first + "__" + second, fields);
    }

    /** The document's decoded fields, or null when it does not exist. */
    public Map<String, Object> fields(String path) {
        ObjectNode fields = documents.get(path);
        return fields == null ? null : FirestoreRestClient.decodeFields(fields);
    }

    /** Paths of the documents directly in a collection, e.g. "calls". */
    public Set<String> paths(String collection) {
        Set<String> paths = new TreeSet<>();
        for (String path : documents.keySet()) {
            if (path.startsWith(collection + "/") && path.indexOf('/', collection.length() + 1) < 0) {
                paths.add(path);
            }
        }
        return paths;
    }

    public List<Push> pushes() {
        return pushes;
    }

    public int commits() {
        return commits;
    }

    /** FCM answers UNREGISTERED for this token from now on. */
    public void uninstall(String token) {
        deadTokens.add(token);
    }

    /** Runs once, right before the next commit is checked: a write that lands between a read and its commit. */
    public void beforeNextCommit(Runnable write) {
        beforeNextCommit = write;
    }

    /** Firestore and FCM stop answering (the network is down). */
    public void unreachable(boolean unreachable) {
        this.unreachable = unreachable;
    }

    // ------------------------------------------------------------------ the HTTP endpoints

    @Override
    public synchronized Response send(Request request) throws IOException {
        String url = request.url();
        if (url.startsWith("https://oauth2.googleapis.com/")) {
            return new Response(200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
        }
        if (!("Bearer " + ACCESS_TOKEN).equals(request.headers().get("Authorization"))) {
            return error(401, "UNAUTHENTICATED");
        }
        if (unreachable) {
            throw new IOException("connection reset");
        }
        if (url.equals(FCM_URL)) {
            return fcm(MAPPER.readTree(request.body()).path("message"));
        }
        if (url.equals(COMMIT_URL)) {
            return commit(MAPPER.readTree(request.body()).path("writes"));
        }
        if (url.startsWith(DOCUMENTS_URL) && request.method().equals("GET")) {
            return get(url.substring(DOCUMENTS_URL.length()));
        }
        throw new AssertionError("unexpected request " + request);
    }

    private Response get(String encodedPath) {
        List<String> segments = new ArrayList<>();
        for (String segment : encodedPath.split("/")) {
            segments.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
        }
        String path = String.join("/", segments);
        ObjectNode fields = documents.get(path);
        if (fields == null) {
            return error(404, "NOT_FOUND");
        }
        ObjectNode document = MAPPER.createObjectNode();
        document.put("name", NAME_PREFIX + path);
        document.set("fields", fields.deepCopy());
        document.put("updateTime", updateTime(path));
        return new Response(200, document.toString());
    }

    private Response commit(JsonNode writes) {
        if (beforeNextCommit != null) {
            Runnable write = beforeNextCommit;
            beforeNextCommit = null;
            write.run();
        }
        // all or nothing: every precondition is checked before anything is written
        for (JsonNode write : writes) {
            String path = pathOf(write);
            JsonNode condition = write.path("currentDocument");
            boolean exists = documents.containsKey(path);
            if (condition.has("exists") && condition.get("exists").asBoolean() != exists) {
                return exists ? error(409, "ALREADY_EXISTS") : error(404, "NOT_FOUND");
            }
            if (condition.has("updateTime") && !(exists && condition.get("updateTime").asText().equals(updateTime(path)))) {
                return error(400, "FAILED_PRECONDITION");
            }
        }
        for (JsonNode write : writes) {
            apply(write);
        }
        commits++;
        return new Response(200, "{\"writeResults\":[],\"commitTime\":\"2026-01-01T00:00:00Z\"}");
    }

    private void apply(JsonNode write) {
        String path = pathOf(write);
        versions.merge(path, 1, Integer::sum);
        if (write.has("delete")) {
            documents.remove(path);
            return;
        }
        ObjectNode document = documents.get(path);
        if (write.has("update")) {
            JsonNode fields = write.get("update").path("fields");
            if (write.has("updateMask")) {
                // only the masked fields change: set when given, removed when not (top-level names only)
                document = document == null ? MAPPER.createObjectNode() : document;
                for (JsonNode masked : write.get("updateMask").path("fieldPaths")) {
                    String field = String.join(".", segments(masked.asText()));
                    if (fields.has(field)) {
                        document.set(field, fields.get(field).deepCopy());
                    } else {
                        document.remove(field);
                    }
                }
            } else {
                document = fields.isObject() ? fields.deepCopy() : MAPPER.createObjectNode();
            }
            documents.put(path, document);
        }
        JsonNode transforms = write.has("updateTransforms")
                ? write.get("updateTransforms") : write.path("transform").path("fieldTransforms");
        for (JsonNode transform : transforms) {
            if (document == null) {
                document = MAPPER.createObjectNode();
                documents.put(path, document);
            }
            List<String> segments = segments(transform.get("fieldPath").asText());
            ObjectNode parent = document;
            for (String segment : segments.subList(0, segments.size() - 1)) {
                ObjectNode map = parent.path(segment).has("mapValue")
                        ? (ObjectNode) parent.get(segment).get("mapValue") : parent.putObject(segment).putObject("mapValue");
                parent = map.has("fields") ? (ObjectNode) map.get("fields") : map.putObject("fields");
            }
            String leaf = segments.get(segments.size() - 1);
            if (transform.has("increment")) {
                long old = Long.parseLong(parent.path(leaf).path("integerValue").asText("0"));
                long by = Long.parseLong(transform.get("increment").path("integerValue").asText());
                parent.putObject(leaf).put("integerValue", Long.toString(old + by));
            } else if (transform.has("removeAllFromArray")) {
                Set<JsonNode> removed = new HashSet<>();
                transform.get("removeAllFromArray").path("values").forEach(removed::add);
                ArrayNode kept = MAPPER.createArrayNode();
                parent.path(leaf).path("arrayValue").path("values").forEach(value -> {
                    if (!removed.contains(value)) {
                        kept.add(value);
                    }
                });
                parent.putObject(leaf).putObject("arrayValue").set("values", kept);
            } else {
                throw new AssertionError("unsupported transform " + transform);
            }
        }
    }

    private Response fcm(JsonNode message) {
        String token = message.path("token").asText();
        if (deadTokens.contains(token)) {
            return new Response(404, "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\","
                    + "\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}");
        }
        Map<String, String> data = new LinkedHashMap<>();
        message.path("data").fields().forEachRemaining(entry -> data.put(entry.getKey(), entry.getValue().asText()));
        JsonNode android = message.path("android");
        pushes.add(new Push(token, data, android.path("ttl").asText(null), android.path("priority").asText(null)));
        return new Response(200, "{\"name\":\"projects/" + PROJECT + "/messages/1\"}");
    }

    private String updateTime(String path) {
        return "v" + versions.getOrDefault(path, 0);
    }

    private static String pathOf(JsonNode write) {
        String name = write.has("update") ? write.get("update").path("name").asText()
                : write.has("delete") ? write.get("delete").asText()
                : write.path("transform").path("document").asText();
        if (!name.startsWith(NAME_PREFIX)) {
            throw new AssertionError("not a document of this project: " + name);
        }
        return name.substring(NAME_PREFIX.length());
    }

    /** Splits a field path at its dots; back-quoted segments keep theirs. */
    static List<String> segments(String fieldPath) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < fieldPath.length(); i++) {
            char c = fieldPath.charAt(i);
            if (quoted && c == '\\' && i + 1 < fieldPath.length()) {
                current.append(fieldPath.charAt(++i));
            } else if (c == '`') {
                quoted = !quoted;
            } else if (c == '.' && !quoted) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        return segments;
    }

    private static Response error(int status, String code) {
        return new Response(status, "{\"error\":{\"code\":" + status + ",\"message\":\"" + code + "\",\"status\":\"" + code + "\"}}");
    }

    /** A throwaway service-account key: the clients need one to sign their OAuth request. */
    private static String serviceAccountJson() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            Map<String, String> json = new LinkedHashMap<>();
            json.put("type", "service_account");
            json.put("project_id", PROJECT);
            json.put("private_key", pem);
            json.put("client_email", "backend@" + PROJECT + ".iam.gserviceaccount.com");
            return MAPPER.writeValueAsString(json);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
