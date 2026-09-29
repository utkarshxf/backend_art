package com.basic.JWTSecurity.account;

import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.repository.ProfileRepository;
import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.service.ChatHttpClient;
import com.basic.JWTSecurity.chat.service.FirestoreRestClient;
import com.basic.JWTSecurity.chat.service.GoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * "Delete account" (Google Play account-deletion policy): removes the account and everything it owns.
 * <ul>
 *   <li>graph: the User, its artist profile with its artworks and galleries (and comments on them), its own
 *       comments and collections; likes, follows and views go with the nodes;</li>
 *   <li>Mongo: the login profile;</li>
 *   <li>chat (Firestore): every conversation the user is in with all its messages, the chat profile and device
 *       tokens, and the chat photos of those conversations in Storage;</li>
 *   <li>Storage: the user's uploaded artwork / profile images;</li>
 *   <li>Firebase Auth: the chat account (uid == username) and the phone / Google sign-in accounts.</li>
 * </ul>
 * Firebase clean-up is best effort (logged, never blocks the deletion); graph and profile deletion must succeed.
 */
@Service
public class AccountDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final String STORAGE_API = "https://storage.googleapis.com/storage/v1/b/";
    private static final String IDENTITY_API = "https://identitytoolkit.googleapis.com/v1/projects/";

    private final Driver driver;
    private final ProfileRepository profileRepository;
    private final ChatFirebaseCredentials credentials;
    private final FirestoreRestClient firestore;
    private final GoogleApiClient google;
    private final ObjectMapper objectMapper;
    private final String storageBucket;

    public AccountDeletionService(Driver driver,
                                  ProfileRepository profileRepository,
                                  ChatFirebaseCredentials credentials,
                                  FirestoreRestClient firestore,
                                  GoogleApiClient google,
                                  ObjectMapper objectMapper,
                                  @Value("${firebase.storage-bucket:}") String storageBucket) {
        this.driver = driver;
        this.profileRepository = profileRepository;
        this.credentials = credentials;
        this.firestore = firestore;
        this.google = google;
        this.objectMapper = objectMapper;
        this.storageBucket = storageBucket == null || storageBucket.isBlank()
                ? credentials.projectId() + ".firebasestorage.app" : storageBucket.trim();
    }

    public void deleteAccount(String username) {
        Optional<Profile> profile = profileRepository.findByUsername(username);
        List<String> images = uploadedImages(username);

        if (credentials.isConfigured()) {
            deleteChat(username);
            deleteStorageObjects(images);
            deleteFirebaseAccounts(username, profile.map(Profile::getPhone).orElse(null),
                    profile.map(Profile::getEmail).orElse(null));
        } else {
            logger.warn("Firebase credentials missing: chat data, images and Firebase accounts of {} were not removed", username);
        }

        deleteGraph(username);
        profile.ifPresent(profileRepository::delete);
        logger.info("Account {} deleted", username);
    }

    // ------------------------------------------------------------------ graph

    private List<String> uploadedImages(String username) {
        try (Session session = driver.session()) {
            Record record = session.executeRead(tx -> tx.run("""
                    MATCH (u:User {id: $username})
                    OPTIONAL MATCH (u)-[:IS_AN]->(artist:Artist)
                    OPTIONAL MATCH (artist)-[:CREATED]->(w:Artwork)
                    RETURN u.profilePicture AS userPicture,
                           collect(DISTINCT artist.image_url) AS artistPictures,
                           collect(DISTINCT w.image_url) + collect(DISTINCT w.image_url_compressed) AS artworkImages
                    """, Values.parameters("username", username)).list().stream().findFirst().orElse(null));
            if (record == null) {
                return List.of();
            }
            Set<String> urls = new LinkedHashSet<>();
            if (!record.get("userPicture").isNull()) {
                urls.add(record.get("userPicture").asString());
            }
            record.get("artistPictures").asList(v -> v.isNull() ? null : v.asString()).forEach(urls::add);
            record.get("artworkImages").asList(v -> v.isNull() ? null : v.asString()).forEach(urls::add);
            urls.remove(null);
            return new ArrayList<>(urls);
        }
    }

    private void deleteGraph(String username) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run("""
                    MATCH (u:User {id: $username})
                    OPTIONAL MATCH (u)-[:IS_AN]->(artist:Artist)
                    OPTIONAL MATCH (artist)-[:CREATED]->(owned)
                    WHERE owned:Artwork OR owned:Gallery
                    WITH u, collect(DISTINCT artist) AS artists, collect(DISTINCT owned) AS owned
                    WITH u, artists, owned,
                         reduce(acc = [], w IN owned | acc + [(c:Comment)-[:HAS_COMMENT]->(w) | c]) AS artworkComments,
                         [(u)-[:POSTED_COMMENT]->(c:Comment) | c] AS ownComments,
                         [(u)-[:CREATED]->(f:Favorites) | f] AS collections
                    WITH u, reduce(acc = [], n IN artworkComments + ownComments + collections + owned + artists |
                                   CASE WHEN n IN acc THEN acc ELSE acc + n END) AS nodes
                    FOREACH (n IN nodes | DETACH DELETE n)
                    DETACH DELETE u
                    """, Values.parameters("username", username)).consume());
        }
    }

    // ------------------------------------------------------------------ chat (Firestore + Storage)

    private void deleteChat(String username) {
        try {
            List<String> conversations = firestore.queryNamesWhereArrayContains("conversations", "usernames", username);
            for (String conversation : conversations) {
                String conversationId = conversation.substring(conversation.lastIndexOf('/') + 1);
                List<String> docs = new ArrayList<>(firestore.listDocumentNames("conversations", conversationId, "messages"));
                docs.add(conversation);
                firestore.deleteDocuments(docs);
                deleteStoragePrefix("chat/" + conversationId + "/");
            }
            firestore.deleteDocuments(List.of(
                    firestore.documentName("users", username, "private", "devices"),
                    firestore.documentName("users", username)));
        } catch (RuntimeException e) {
            logger.warn("Chat clean-up for {} failed: {}", username, e.toString());
        }
    }

    private void deleteStoragePrefix(String prefix) {
        String list = STORAGE_API + encode(storageBucket) + "/o?fields=items(name),nextPageToken&prefix=" + encode(prefix);
        String pageToken = null;
        do {
            ChatHttpClient.Response response = google.send("GET", pageToken == null ? list : list + "&pageToken=" + encode(pageToken), null);
            if (!response.isSuccess()) {
                logger.warn("Listing Storage {} failed: HTTP {}", prefix, response.status());
                return;
            }
            JsonNode page = readJson(response.body());
            List<String> names = new ArrayList<>();
            page.path("items").forEach(item -> names.add(item.path("name").asText()));
            names.forEach(this::deleteStorageObject);
            pageToken = page.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isEmpty());
    }

    private void deleteStorageObjects(List<String> downloadUrls) {
        downloadUrls.stream()
                .map(url -> storageObjectName(url, storageBucket))
                .flatMap(Optional::stream)
                .distinct()
                .forEach(this::deleteStorageObject);
    }

    private void deleteStorageObject(String name) {
        try {
            ChatHttpClient.Response response = google.send("DELETE", STORAGE_API + encode(storageBucket) + "/o/" + encode(name), null);
            if (!response.isSuccess() && response.status() != 404) {
                logger.warn("Deleting Storage object {} failed: HTTP {}", name, response.status());
            }
        } catch (RuntimeException e) {
            logger.warn("Deleting Storage object {} failed: {}", name, e.toString());
        }
    }

    /**
     * Object name of a Firebase Storage download URL in our bucket
     * (https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{encoded name}?alt=media&token=...), else empty.
     */
    static Optional<String> storageObjectName(String url, String bucket) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url.trim());
            if (!"firebasestorage.googleapis.com".equalsIgnoreCase(uri.getHost())) {
                return Optional.empty();
            }
            String prefix = "/v0/b/" + bucket + "/o/";
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(prefix) || path.length() == prefix.length()) {
                return Optional.empty();
            }
            return Optional.of(URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ Firebase Auth

    private void deleteFirebaseAccounts(String username, String phone, String email) {
        try {
            ObjectNode lookup = objectMapper.createObjectNode();
            lookup.putArray("localId").add(username);
            if (phone != null && !phone.isBlank()) {
                lookup.putArray("phoneNumber").add(phone);
            }
            if (email != null && !email.isBlank()) {
                lookup.putArray("email").add(email);
            }
            String base = IDENTITY_API + encode(credentials.projectId()) + "/accounts";
            ChatHttpClient.Response found = google.send("POST", base + ":lookup", lookup.toString());
            if (!found.isSuccess()) {
                logger.warn("Firebase account lookup for {} failed: HTTP {}", username, found.status());
                return;
            }
            Set<String> uids = new LinkedHashSet<>();
            readJson(found.body()).path("users").forEach(user -> uids.add(user.path("localId").asText()));
            uids.remove("");
            for (String uid : uids) {
                ObjectNode delete = objectMapper.createObjectNode().put("localId", uid);
                ChatHttpClient.Response deleted = google.send("POST", base + ":delete", delete.toString());
                if (!deleted.isSuccess()) {
                    logger.warn("Deleting a Firebase account of {} failed: HTTP {}", username, deleted.status());
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Firebase account clean-up for {} failed: {}", username, e.toString());
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
