package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Favorites;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetFavorites;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    @Query("MATCH (user:User {id: $userId})-[:CREATED]->(favorites:Favorites) " +
            "RETURN  favorites.id AS id ,"+
            "favorites.title AS title ,"+
            "favorites.description AS description"
    )
    List<GetFavorites> getFavoritesByUserId(@Param("userId") String userId);

    @Query("""
            MATCH (favorites:Favorites {id: $favoriteId})-[:CONTAINS]->(artwork:Artwork)
            RETURN 
                artwork.id AS id,
                    artwork.title AS title,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.storageType AS storageType,
                    artwork.releasedDate AS releasedDate,
                    artwork.type AS type,
                    artwork.medium AS medium,
                    artwork.dimensions AS dimensions,
                    artwork.artist AS artist, 
                    artwork.current_location AS current_location,
                    artwork.period_style AS period_style,
                    artwork.art_movement AS art_movement,
                    artwork.image_url_compressed AS image_url_compressed,
                    artwork.image_url AS image_url,
                    artwork.license_info AS license_info,
                    artwork.source_url AS source_url,
                CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked
            ORDER BY artwork.name ASC
            """)
    List<GetArtwork> getArtworksByFavoriteId(@Param("favoriteId") String favoriteId);
}
