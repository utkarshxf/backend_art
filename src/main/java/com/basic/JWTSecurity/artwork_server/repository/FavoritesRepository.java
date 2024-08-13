package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Favorites;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface FavoritesRepository extends Neo4jRepository<Favorites,String> {



    @Query("MATCH (user: User {id: $userId}), (favorites: Favorites {id: $favoritesId})" +
            " MERGE (user)-[:CREATED {createdAt: $createdAt}]->(favorites)")
    void addFavoritesAndUserRelationship(@Param("favoritesId")String favoritesId,
                                         @Param("userId")String userId,
                                         @Param("createdAt") LocalDateTime createdAt);


    @Query("MATCH (artwork: Artwork {id: $artworkId}), (favorites: Favorites {id: $favoritesId})" +
            " MERGE (favorites)-[:CONTAINS {createdAt: $createdAt}]->(artwork)")
    void addArtworkToFavorites(@Param("favoritesId")String favoritesId,
                               @Param("artworkId")String artworkId,
                               @Param("createdAt") LocalDateTime createdAt);
    @Query("MATCH (artwork: Artwork {id: $artworkId}) <-[relationship:CONTAINS]- (favorites: Favorites {id: $favoritesId})" +
            " DELETE relationship")
    void removeArtworkFromFavorites(@Param("favoritesId")String favoritesId,
                                    @Param("artworkId")String artworkId);
}
