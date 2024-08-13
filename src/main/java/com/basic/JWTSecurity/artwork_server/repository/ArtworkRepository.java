package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArtworkRepository extends Neo4jRepository<Artwork, String> {

    @Query("MATCH (artwork:Artwork {id: $artworkId}) RETURN artwork.id As id, artwork.name As name, artwork.description As description, artwork.status As status, artwork.duration As duration, artwork.storageId As storageId, artwork.storageType As storageType, artwork.type As type, artwork.year As year")
    Optional<ArtworkProjection> findByIdProjection(@Param("artworkId") String id);



    @Query("MATCH (user: User {id : $userId}) " +
            "WITH user " +
            "MATCH (artwork: Artwork {id: $artworkId}) " +
            "MERGE (user) -[:LIKES {createdAt: $createdAt}]-> (artwork)")
    void userLikeAArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId, @Param("createdAt")LocalDateTime createdAt);

    @Query("MATCH (artwork:Artwork {id: $artworkId})<-[relationship:LIKES]- (user: User {id : $userId})" +
            " DELETE relationship")
    void userDisLikeAArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId);


    @Query(
            "MATCH (user:User {id: $userId}) " +
                    "CALL { " +
                    "WITH user " +
                    "MATCH (user)-[:LIKES]->(artwork:Artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(recommendArtwork:Artwork) " +
                    "WHERE NOT (user)-[:LIKES]->(recommendArtwork) " +
                    "WITH DISTINCT recommendArtwork, count(DISTINCT otherUser) AS userSimilarity " +
                    "RETURN recommendArtwork, userSimilarity, 1 AS priority " +
                    "UNION " +
                    "MATCH (artwork:Artwork) " +
                    "WHERE NOT (:User {id: $userId})-[:LIKES]->(artwork) " +
                    "AND NOT ((:User {id: $userId})-[:LIKES]->(:Artwork)<-[:LIKES]-(:User)-[:LIKES]->(artwork)) " +
                    "RETURN artwork AS recommendArtwork, 0 AS userSimilarity, 2 AS priority " +
                    "} " +
                    "WITH recommendArtwork, userSimilarity, priority " +
                    "OPTIONAL MATCH (recommendArtwork)<-[:LIKES]-(likeUser:User) " +
                    "WITH recommendArtwork, userSimilarity, priority, count(likeUser) AS likes " +
                    "RETURN recommendArtwork, userSimilarity, likes " +
                    "ORDER BY priority, userSimilarity DESC, likes DESC " +
                    "SKIP $skip LIMIT $limit"
    )
    List<Artwork> recommendArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );
}