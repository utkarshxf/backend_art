package com.basic.JWTSecurity.artwork_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Search-as-you-type over artworks (title, artist, medium, movement and description), artists and people, backed by
 * Neo4j full-text indexes (created by {@code Neo4jIndexes}). Each word must match, as a whole word, a prefix (the
 * word being typed) or with one typo. If a full-text index is not online yet, a plain CONTAINS scan answers instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    public static final String ARTWORK_INDEX = "artwork_search";
    public static final String ARTIST_INDEX = "artist_search";
    public static final String USER_INDEX = "user_search";

    private static final int MAX_TERMS = 8;
    private static final int SNIPPET_LENGTH = 140;
    private static final int SNIPPET_LEAD = 40;
    private static final int ALL_ARTISTS = 6;
    private static final int ALL_PEOPLE = 6;

    private final Driver driver;

    public record ArtworkHit(String id, String title, String artist, String imageUrl, String medium, String year,
                             String snippet) {}

    // username: set when the artist belongs to an Artistry account
    public record ArtistHit(String id, String name, String imageUrl, String nationality, String artMovement,
                            String username) {}

    // artistId: set when this account is also an artist
    public record PersonHit(String username, String name, String profilePicture, String artistId) {}

    public record SearchResult(List<ArtworkHit> artworks, List<ArtistHit> artists, List<PersonHit> people) {}

    public enum Type { ALL, ARTWORKS, ARTISTS, PEOPLE }

    public SearchResult search(String query, Type type, int skip, int limit) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            return new SearchResult(List.of(), List.of(), List.of());
        }
        boolean all = type == Type.ALL;
        List<ArtworkHit> artworks = all || type == Type.ARTWORKS
                ? artworks(terms, all ? 0 : skip, limit) : List.of();
        List<ArtistHit> artists = all || type == Type.ARTISTS
                ? artists(terms, all ? 0 : skip, all ? ALL_ARTISTS : limit) : List.of();
        List<PersonHit> people = all || type == Type.PEOPLE
                ? people(terms, all ? 0 : skip, all ? ALL_PEOPLE : limit) : List.of();
        return new SearchResult(artworks, artists, people);
    }

    // ------------------------------------------------------------------ queries

    private List<ArtworkHit> artworks(List<String> terms, int skip, int limit) {
        String returns = """
                RETURN node.id AS id, node.title AS title, node.artist AS artist,
                       coalesce(node.image_url_compressed, node.image_url) AS imageUrl, node.medium AS medium,
                       toString(node.releasedDate) AS released, node.description AS description
                """;
        String visible = "NOT coalesce(node.status, '') IN ['DELETED', 'BLOCKED']";
        return run(
                "CALL db.index.fulltext.queryNodes($index, $q) YIELD node, score WHERE " + visible + " "
                        + returns + " ORDER BY score DESC SKIP $skip LIMIT $limit",
                "MATCH (node:Artwork) WHERE " + visible + " AND " + containsAll(
                        "node.title", "node.artist", "node.medium", "node.art_movement", "node.description") + " "
                        + returns + " ORDER BY node.title SKIP $skip LIMIT $limit",
                ARTWORK_INDEX, terms, skip, limit,
                r -> new ArtworkHit(
                        str(r, "id"), str(r, "title"), str(r, "artist"), str(r, "imageUrl"), str(r, "medium"),
                        year(str(r, "released")),
                        snippet(str(r, "description"), str(r, "title") + " " + str(r, "artist") + " " + str(r, "medium"), terms)));
    }

    private List<ArtistHit> artists(List<String> terms, int skip, int limit) {
        String returns = """
                OPTIONAL MATCH (account:User)-[:IS_AN]->(node)
                WITH node, score, head(collect(account.id)) AS username
                RETURN node.id AS id, node.name AS name, node.image_url AS imageUrl,
                       node.nationality AS nationality, node.art_movement AS artMovement, username
                """;
        return run(
                "CALL db.index.fulltext.queryNodes($index, $q) YIELD node, score " + returns
                        + " ORDER BY score DESC SKIP $skip LIMIT $limit",
                "MATCH (node:Artist) WHERE " + containsAll("node.name", "node.nationality", "node.art_movement")
                        + " WITH node, 1.0 AS score " + returns + " ORDER BY name SKIP $skip LIMIT $limit",
                ARTIST_INDEX, terms, skip, limit,
                r -> new ArtistHit(str(r, "id"), str(r, "name"), str(r, "imageUrl"), str(r, "nationality"),
                        str(r, "artMovement"), str(r, "username")));
    }

    private List<PersonHit> people(List<String> terms, int skip, int limit) {
        String returns = """
                OPTIONAL MATCH (node)-[:IS_AN]->(artist:Artist)
                WITH node, score, head(collect(artist.id)) AS artistId
                RETURN node.id AS username, node.name AS name, node.profilePicture AS profilePicture, artistId
                """;
        return run(
                "CALL db.index.fulltext.queryNodes($index, $q) YIELD node, score " + returns
                        + " ORDER BY score DESC SKIP $skip LIMIT $limit",
                "MATCH (node:User) WHERE " + containsAll("node.id", "node.name")
                        + " WITH node, 1.0 AS score " + returns + " ORDER BY username SKIP $skip LIMIT $limit",
                USER_INDEX, terms, skip, limit,
                r -> new PersonHit(str(r, "username"), str(r, "name"), str(r, "profilePicture"), str(r, "artistId")));
    }

    private <T> List<T> run(String fulltextCypher, String fallbackCypher, String index, List<String> terms,
                            int skip, int limit, Function<Record, T> mapper) {
        Map<String, Object> params = Map.of(
                "index", index, "q", luceneQuery(terms), "terms", terms, "skip", skip, "limit", limit);
        try (Session session = driver.session()) {
            try {
                return session.executeRead(tx -> tx.run(fulltextCypher, params).list(mapper::apply));
            } catch (RuntimeException e) {
                // e.g. the index is still being built right after a deploy
                log.warn("Full-text search on {} failed ({}); falling back to CONTAINS", index, e.getMessage());
                return session.executeRead(tx -> tx.run(fallbackCypher, params).list(mapper::apply));
            }
        }
    }

    // Every term appears (case-insensitively) in at least one of the given properties
    private static String containsAll(String... properties) {
        String anyProperty = Arrays.stream(properties)
                .map(p -> "toLower(coalesce(" + p + ", '')) CONTAINS t")
                .collect(Collectors.joining(" OR "));
        return "ALL(t IN $terms WHERE " + anyProperty + ")";
    }

    // ------------------------------------------------------------------ query text

    /** Lowercase, accent-free words (letters/digits only, so nothing needs Lucene escaping). */
    static List<String> terms(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(fold(raw).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> !t.isBlank())
                .distinct()
                .limit(MAX_TERMS)
                .toList();
    }

    /** Each word: exact (boosted), as a prefix while typing, and with one typo when it is long enough. */
    static String luceneQuery(List<String> terms) {
        return terms.stream()
                .map(t -> "(" + t + "^3 OR " + t + "*" + (t.length() >= 4 ? " OR " + t + "~1" : "") + ")")
                .collect(Collectors.joining(" AND "));
    }

    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * When the artwork matched through its description (a term that its title/artist/medium don't explain), a short
     * excerpt around that word so the app can show why it matched; null otherwise.
     */
    static String snippet(String description, String shownText, List<String> terms) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String shown = fold(shownText == null ? "" : shownText);
        List<String> unexplained = terms.stream().filter(t -> !shown.contains(t)).toList();
        if (unexplained.isEmpty()) {
            return null;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        int at = unexplained.stream().mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(0);
        int start = Math.max(0, at - SNIPPET_LEAD);
        if (start > 0) {
            int space = description.indexOf(' ', start);
            if (space >= 0 && space < at) {
                start = space + 1;
            }
        }
        int end = Math.min(description.length(), start + SNIPPET_LENGTH);
        if (end < description.length()) {
            int space = description.lastIndexOf(' ', end);
            if (space > at) {
                end = space;
            }
        }
        String body = description.substring(start, end).strip().replaceAll("\\s+", " ");
        return (start > 0 ? "…" : "") + body + (end < description.length() ? "…" : "");
    }

    static String year(String released) {
        return released != null && released.length() >= 4 && released.substring(0, 4).chars().allMatch(Character::isDigit)
                ? released.substring(0, 4) : null;
    }

    private static String str(Record r, String key) {
        Value v = r.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        // imported data is not always typed consistently (numbers, lists); never fail a search over it
        Object o = v.asObject();
        return o instanceof String s ? s : String.valueOf(o);
    }
}
