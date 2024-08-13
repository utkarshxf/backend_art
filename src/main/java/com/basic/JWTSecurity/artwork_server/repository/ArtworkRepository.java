package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

}
