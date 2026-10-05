package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Firestore REST v1 with the service account (bypasses security rules): reads single documents, removes values
 * from an array field, and commits atomic writes with preconditions (calls). Documents are addressed by their path
 * segments, e.g. {@code ("conversations", cid)}.
 */
@Component
public class FirestoreRestClient {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/";
    private static final ObjectMapper ERROR_READER = new ObjectMapper();
    /** A commit Firestore refused because a precondition did not hold or it collided with another write. */
    private static final Set<String> CONFLICTS = Set.of("FAILED_PRECONDITION", "ALREADY_EXISTS", "NOT_FOUND", "ABORTED");
    private static final Pattern PLAIN_FIELD = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");

    private static final Logger logger = LoggerFactory.getLogger(FirestoreRestClient.class);

    private final GoogleApiClient api;
    private final ChatFirebaseCredentials credentials;
    private final ObjectMapper objectMapper;

    public FirestoreRestClient(GoogleApiClient api, ChatFirebaseCredentials credentials, ObjectMapper objectMapper) {
        this.api = api;
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    /**
     * The document's fields decoded to plain Java values (see {@link #decodeValue}), or empty when it does not exist.
     * Any other failure is a {@link ChatException#upstream()}.
     */
    public Optional<Map<String, Object>> getDocument(String... path) {
        return getSnapshot(path).map(Snapshot::fields);
    }

    /** A document's decoded fields with the version they were read at. */
    public record Snapshot(Map<String, Object> fields, String updateTime) {
    }

    /** {@link #getDocument} plus the document's update time, the precondition of {@link Write#ifUnchangedSince}. */
    public Optional<Snapshot> getSnapshot(String... path) {
        String url = BASE_URL + databasePath(true) + "/documents/"
                + Stream.of(path).map(FirestoreRestClient::encodeSegment).collect(Collectors.joining("/"));
        ChatHttpClient.Response response = api.send("GET", url, null);
        if (response.status() == 404) {
            return Optional.empty();
        }
        if (!response.isSuccess()) {
            logger.error("Firestore read of {} failed: HTTP {} {}", String.join("/", path), response.status(),
                    errorSummary(response.body()));
            throw ChatException.upstream();
        }
        JsonNode document = readJson(response.body());
        return Optional.of(new Snapshot(decodeFields(document.path("fields")), document.path("updateTime").asText("")));
    }

    /** One write of a {@link #commit}, made by {@link #create} or {@link #update}. */
    public static final class Write {
        private final ObjectNode node;

        private Write(ObjectNode node) {
            this.node = node;
        }

        /** Refuse the whole commit if the document was written after it was read ({@link Snapshot#updateTime}). */
        public Write ifUnchangedSince(String updateTime) {
            node.putObject("currentDocument").put("updateTime", updateTime);
            return this;
        }

        /** Adds {@code by} to a number field (a missing field counts as 0); see {@link #fieldPath}. */
        public Write increment(String fieldPath, long by) {
            ArrayNode transforms = node.has("updateTransforms")
                    ? (ArrayNode) node.get("updateTransforms") : node.putArray("updateTransforms");
            ObjectNode transform = transforms.addObject();
            transform.put("fieldPath", fieldPath);
            transform.putObject("increment").put("integerValue", Long.toString(by));
            return this;
        }
    }

    /** Writes a new document; the commit is refused if it already exists. Values: see {@link #encodeValue}. */
    public Write create(Map<String, Object> fields, String... path) {
        ObjectNode write = objectMapper.createObjectNode();
        ObjectNode document = write.putObject("update");
        document.put("name", documentName(path));
        document.set("fields", encodeFields(fields));
        write.putObject("currentDocument").put("exists", false);
        return new Write(write);
    }

    /**
     * Replaces exactly the given top-level fields of an existing document (the others are kept); the commit is
     * refused if the document does not exist.
     */
    public Write update(Map<String, Object> fields, String... path) {
        ObjectNode write = objectMapper.createObjectNode();
        ObjectNode document = write.putObject("update");
        document.put("name", documentName(path));
        document.set("fields", encodeFields(fields));
        ArrayNode mask = write.putObject("updateMask").putArray("fieldPaths");
        fields.keySet().forEach(field -> mask.add(fieldPath(field)));
        write.putObject("currentDocument").put("exists", true);
        return new Write(write);
    }

    /**
     * Applies the writes atomically. False when a precondition did not hold (a document was created, changed or
     * deleted meanwhile): nothing was written and the caller should read again and decide anew. Any other failure is
     * a {@link ChatException#upstream()}.
     */
    public boolean commit(List<Write> writes) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode array = body.putArray("writes");
        writes.forEach(write -> array.add(write.node));
        ChatHttpClient.Response response = api.send("POST", BASE_URL + databasePath(true) + "/documents:commit", body.toString());
        if (response.isSuccess()) {
            return true;
        }
        if (CONFLICTS.contains(readJson(response.body()).path("error").path("status").asText(""))) {
            return false;
        }
        logger.error("Firestore commit of {} write(s) failed: HTTP {} {}", writes.size(), response.status(),
                errorSummary(response.body()));
        throw ChatException.upstream();
    }

    /**
     * A field path for update masks and transforms, e.g. {@code fieldPath("unread", username)}: segments that are
     * not plain identifiers (usernames with dots, dashes, ...) are back-quoted.
     */
    public static String fieldPath(String... segments) {
        return Stream.of(segments)
                .map(segment -> PLAIN_FIELD.matcher(segment).matches()
                        ? segment : "`" + segment.replace("\\", "\\\\").replace("`", "\\`") + "`")
                .collect(Collectors.joining("."));
    }

    /**
     * Removes every occurrence of {@code values} from the string array {@code field} (arrayRemove), only if the
     * document still exists. Best effort: failures are logged, not thrown.
     */
    public void removeFromArray(String field, List<String> values, String... path) {
        if (values.isEmpty()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode write = body.putArray("writes").addObject();
        ObjectNode transform = write.putObject("transform");
        transform.put("document", databasePath(false) + "/documents/" + String.join("/", path));
        ObjectNode fieldTransform = transform.putArray("fieldTransforms").addObject();
        fieldTransform.put("fieldPath", field);
        ArrayNode removed = fieldTransform.putObject("removeAllFromArray").putArray("values");
        values.forEach(value -> removed.addObject().put("stringValue", value));
        // without the precondition a transform would create the document when it was deleted meanwhile
        write.putObject("currentDocument").put("exists", true);

        ChatHttpClient.Response response;
        try {
            response = api.send("POST", BASE_URL + databasePath(true) + "/documents:commit", body.toString());
        } catch (ChatException e) {
            logger.warn("Could not remove {} value(s) from {}.{}", values.size(), String.join("/", path), field);
            return;
        }
        if (!response.isSuccess()) {
            logger.warn("Could not remove {} value(s) from {}.{}: HTTP {} {}", values.size(), String.join("/", path),
                    field, response.status(), errorSummary(response.body()));
        }
    }

    /**
     * Full resource names of the documents in {@code collectionId} whose array {@code field} contains {@code value}
     * (ARRAY_CONTAINS query). Failures are a {@link ChatException#upstream()}.
     */
    public List<String> queryNamesWhereArrayContains(String collectionId, String field, String value) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode query = body.putObject("structuredQuery");
        query.putArray("from").addObject().put("collectionId", collectionId);
        query.putObject("select").putArray("fields").addObject().put("fieldPath", "__name__");
        ObjectNode filter = query.putObject("where").putObject("fieldFilter");
        filter.putObject("field").put("fieldPath", field);
        filter.put("op", "ARRAY_CONTAINS");
        filter.putObject("value").put("stringValue", value);
        ChatHttpClient.Response response = api.send("POST", BASE_URL + databasePath(true) + "/documents:runQuery", body.toString());
        if (!response.isSuccess()) {
            logger.error("Firestore query on {} failed: HTTP {} {}", collectionId, response.status(), errorSummary(response.body()));
            throw ChatException.upstream();
        }
        List<String> names = new java.util.ArrayList<>();
        for (JsonNode row : readJson(response.body())) {
            String name = row.path("document").path("name").asText("");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /** Full resource names of every document in a collection, e.g. ("conversations", cid, "messages"). */
    public List<String> listDocumentNames(String... collectionPath) {
        String base = BASE_URL + databasePath(true) + "/documents/"
                + Stream.of(collectionPath).map(FirestoreRestClient::encodeSegment).collect(Collectors.joining("/"))
                + "?pageSize=300&mask.fieldPaths=__name__";
        List<String> names = new java.util.ArrayList<>();
        String pageToken = null;
        do {
            String url = pageToken == null ? base : base + "&pageToken=" + encodeSegment(pageToken);
            ChatHttpClient.Response response = api.send("GET", url, null);
            if (!response.isSuccess()) {
                logger.error("Firestore list of {} failed: HTTP {} {}", String.join("/", collectionPath), response.status(),
                        errorSummary(response.body()));
                throw ChatException.upstream();
            }
            JsonNode page = readJson(response.body());
            page.path("documents").forEach(doc -> names.add(doc.path("name").asText()));
            pageToken = page.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isEmpty());
        return names;
    }

    /** Deletes documents by full resource name, in batches of 500 (a missing document is not an error). */
    public void deleteDocuments(List<String> names) {
        for (int from = 0; from < names.size(); from += 500) {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode writes = body.putArray("writes");
            names.subList(from, Math.min(names.size(), from + 500)).forEach(name -> writes.addObject().put("delete", name));
            ChatHttpClient.Response response = api.send("POST", BASE_URL + databasePath(true) + "/documents:commit", body.toString());
            if (!response.isSuccess()) {
                logger.error("Firestore delete of {} document(s) failed: HTTP {} {}", writes.size(), response.status(),
                        errorSummary(response.body()));
                throw ChatException.upstream();
            }
        }
    }

    /** Whether {@code id} can be a document id that is safe to put in a REST path (usernames, conversation and call ids). */
    public static boolean isDocumentId(String id) {
        return id != null && !id.isBlank() && id.length() <= 300
                && !id.contains("/") && !id.equals(".") && !id.equals("..")
                && !(id.startsWith("__") && id.endsWith("__"))
                && id.chars().noneMatch(Character::isISOControl);
    }

    /** Full resource name of a document path, e.g. ("users", "alice") -> projects/p/databases/(default)/documents/users/alice */
    public String documentName(String... path) {
        return databasePath(false) + "/documents/" + String.join("/", path);
    }

    private String databasePath(boolean encoded) {
        String project = credentials.projectId();
        return "projects/" + (encoded ? encodeSegment(project) : project) + "/databases/(default)";
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode readJson(String body) {
        try {
            JsonNode node = body == null || body.isBlank() ? null : objectMapper.readTree(body);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (IOException e) {
            logger.error("Firestore answered with invalid JSON");
            throw ChatException.upstream();
        }
    }

    /** "STATUS: message" of a Google API error body, for logs. */
    static String errorSummary(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode error = ERROR_READER.readTree(body).path("error");
            String summary = (error.path("status").asText("") + ": " + error.path("message").asText("")).trim();
            return summary.length() > 300 ? summary.substring(0, 300) : summary;
        } catch (IOException e) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }

    /** The reverse of {@link #decodeFields}. */
    static ObjectNode encodeFields(Map<String, ?> fields) {
        ObjectNode encoded = JsonNodeFactory.instance.objectNode();
        fields.forEach((name, value) -> encoded.set(name, encodeValue(value)));
        return encoded;
    }

    /**
     * A Java value as a typed Firestore value: String, Boolean, Integer / Long (integerValue), other numbers
     * (doubleValue), Instant (timestampValue), Map with string keys (mapValue), Collection (arrayValue), null.
     */
    static ObjectNode encodeValue(Object value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (value == null) {
            node.putNull("nullValue");
        } else if (value instanceof String string) {
            node.put("stringValue", string);
        } else if (value instanceof Boolean bool) {
            node.put("booleanValue", bool);
        } else if (value instanceof Integer || value instanceof Long) {
            // 64-bit integers travel as JSON strings
            node.put("integerValue", value.toString());
        } else if (value instanceof Number number) {
            node.put("doubleValue", number.doubleValue());
        } else if (value instanceof Instant instant) {
            node.put("timestampValue", instant.toString());
        } else if (value instanceof Map<?, ?> map) {
            ObjectNode fields = node.putObject("mapValue").putObject("fields");
            map.forEach((name, element) -> fields.set(String.valueOf(name), encodeValue(element)));
        } else if (value instanceof Collection<?> collection) {
            ArrayNode values = node.putObject("arrayValue").putArray("values");
            collection.forEach(element -> values.add(encodeValue(element)));
        } else {
            throw new IllegalArgumentException("Not a Firestore value: " + value.getClass().getName());
        }
        return node;
    }

    /** Decodes a document's {@code fields} object: {@code {"name": {"stringValue": "x"}}} becomes {@code {name=x}}. */
    static Map<String, Object> decodeFields(JsonNode fields) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        if (fields == null || !fields.isObject()) {
            return decoded;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = fields.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            decoded.put(entry.getKey(), decodeValue(entry.getValue()));
        }
        return decoded;
    }

    /**
     * One typed Firestore value as a Java value: string, reference and bytes values → String, booleanValue →
     * Boolean, integerValue (a JSON string) → Long, doubleValue → Double, timestampValue → Instant, mapValue → Map
     * (an empty map has no "fields"), arrayValue → List (an empty array has no "values"), nullValue and anything
     * unknown → null.
     */
    static Object decodeValue(JsonNode value) {
        if (value == null || !value.isObject()) {
            return null;
        }
        if (value.has("stringValue")) {
            return value.get("stringValue").asText();
        }
        if (value.has("booleanValue")) {
            return value.get("booleanValue").asBoolean();
        }
        if (value.has("integerValue")) {
            try {
                return Long.parseLong(value.get("integerValue").asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value.has("doubleValue")) {
            return value.get("doubleValue").asDouble();
        }
        if (value.has("timestampValue")) {
            try {
                return Instant.parse(value.get("timestampValue").asText());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (value.has("mapValue")) {
            return decodeFields(value.get("mapValue").path("fields"));
        }
        if (value.has("arrayValue")) {
            List<Object> list = new ArrayList<>();
            JsonNode values = value.get("arrayValue").path("values");
            if (values.isArray()) {
                values.forEach(element -> list.add(decodeValue(element)));
            }
            return list;
        }
        if (value.has("referenceValue")) {
            return value.get("referenceValue").asText();
        }
        if (value.has("bytesValue")) {
            return value.get("bytesValue").asText();
        }
        return null;
    }
}
