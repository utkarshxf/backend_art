package com.basic.JWTSecurity.artwork_server.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

// Multi-column admin overview reads go through Neo4jClient. On this Spring Data Neo4j version a
// @Query repository method with several result columns is either bound onto the domain entity
// (NotReadablePropertyException) or refused for Map ("Records with more than one value cannot be
// converted without a mapper"); Neo4jClient's fetch().all() returns plain row maps. Single-column
// counts stay in the @Query repositories - those are confirmed working on the live graph.
@Component
@RequiredArgsConstructor
public class AdminGraphQueries {

    private final Neo4jClient neo4jClient;

    // Artworks reached through the CREATED edge only (every ingestion path creates it); traversal-based,
    // because an OPTIONAL MATCH over all Artwork nodes per artist is a cartesian product and hangs.
    public Collection<Map<String, Object>> countArtworksPerArtist() {
        return neo4jClient.query(
                "MATCH (artist:Artist) " +
                "OPTIONAL MATCH (artist)-[:CREATED]->(artwork:Artwork) " +
                "WHERE coalesce(artwork.status, 'APPROVED') <> 'DELETED' " +
                "RETURN artist.id AS id, artist.name AS name, count(artwork) AS artworkCount " +
                "ORDER BY artworkCount DESC, name ASC"
        ).fetch().all();
    }

    public Collection<Map<String, Object>> userArtists() {
        return neo4jClient.query(
                "MATCH (u:User)-[:IS_AN]->(artist:Artist) " +
                "RETURN u.id AS id, artist.id AS artistId, artist.name AS artistName"
        ).fetch().all();
    }

    public Collection<Map<String, Object>> userArtworkCounts() {
        return neo4jClient.query(
                "MATCH (u:User)-[:IS_AN]->(:Artist)-[:CREATED]->(w:Artwork) " +
                "WHERE coalesce(w.status, 'APPROVED') <> 'DELETED' " +
                "RETURN u.id AS id, count(w) AS count"
        ).fetch().all();
    }

    // LIKES is labelled Artwork because users also LIKE genres and galleries.
    // Variable is named rel, not like: LIKE is a reserved keyword in newer Cypher versions.
    public Collection<Map<String, Object>> userLikeCounts() {
        return neo4jClient.query(
                "MATCH (u:User)-[rel:LIKES]->(:Artwork) RETURN u.id AS id, count(rel) AS count"
        ).fetch().all();
    }

    public Collection<Map<String, Object>> userFollowerCounts() {
        return neo4jClient.query(
                "MATCH (u:User)-[:IS_AN]->(:Artist)<-[rel:FOLLOWS]-(:User) RETURN u.id AS id, count(rel) AS count"
        ).fetch().all();
    }

    public Collection<Map<String, Object>> userCommentCounts() {
        return neo4jClient.query(
                "MATCH (u:User)-[rel:POSTED_COMMENT]->(:Comment) RETURN u.id AS id, count(rel) AS count"
        ).fetch().all();
    }

    public Collection<Map<String, Object>> userArtworks(String userId, int limit) {
        return neo4jClient.query(
                "MATCH (:User {id: $userId})-[:IS_AN]->(artist:Artist)-[:CREATED]->(w:Artwork) " +
                "OPTIONAL MATCH (:User)-[rel:LIKES]->(w) " +
                "RETURN w.id AS id, w.title AS title, w.status AS status, toString(w.releasedDate) AS releasedDate, " +
                "w.image_url_compressed AS imageUrlCompressed, count(rel) AS likeCount " +
                "ORDER BY likeCount DESC, w.id ASC LIMIT $limit"
        ).bind(userId).to("userId").bind(limit).to("limit").fetch().all();
    }

    public Collection<Map<String, Object>> userRecentLikes(String userId, int limit) {
        return neo4jClient.query(
                "MATCH (u:User {id: $userId})-[rel:LIKES]->(w:Artwork) " +
                "RETURN w.id AS id, w.title AS title, w.artist AS artistId, toString(rel.createdAt) AS likedAt " +
                "ORDER BY rel.createdAt DESC LIMIT $limit"
        ).bind(userId).to("userId").bind(limit).to("limit").fetch().all();
    }

    public Collection<Map<String, Object>> userArtist(String userId) {
        return neo4jClient.query(
                "MATCH (u:User {id: $userId})-[:IS_AN]->(artist:Artist) " +
                "RETURN artist.id AS id, artist.name AS name, artist.image_url AS image_url"
        ).bind(userId).to("userId").fetch().all();
    }
}
