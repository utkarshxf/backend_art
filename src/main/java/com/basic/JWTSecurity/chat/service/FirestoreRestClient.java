package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Firestore REST v1 with the service account (bypasses security rules): reads single documents and removes values
 * from an array field. Documents are addressed by their path segments, e.g. {@code ("conversations", cid)}.
 */
@Component
public class FirestoreRestClient {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/";
    private static final ObjectMapper ERROR_READER = new ObjectMapper();

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
        return Optional.of(decodeFields(document.path("fields")));
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
