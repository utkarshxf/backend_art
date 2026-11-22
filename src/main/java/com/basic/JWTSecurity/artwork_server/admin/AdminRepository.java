package com.basic.JWTSecurity.artwork_server.admin;

import com.basic.JWTSecurity.artwork_server.model.Artwork;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminRepository extends Neo4jRepository<Artwork, String> {

    @Query("MATCH (artist:Artist {id: $artistId}), (artwork:Artwork {id: $artworkId}) " +
            "MERGE (artist)-[rel:CREATED]->(artwork) ON CREATE SET rel.createdAt = $createdAt")
    void linkArtistToArtwork(@Param("artistId") String artistId,
                             @Param("artworkId") String artworkId,
                             @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artwork:Artwork {id: $artworkId}), (genre:Genre {id: $genreId}) " +
            "MERGE (artwork)-[rel:BELONGS_TO_GENRE]->(genre) ON CREATE SET rel.createdAt = $createdAt")
    void linkArtworkToGenre(@Param("artworkId") String artworkId,
                            @Param("genreId") String genreId,
                            @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artist:Artist {id: $artistId}), (genre:Genre {id: $genreId}) " +
            "MERGE (artist)-[rel:BELONGS_TO_GENRE]->(genre) ON CREATE SET rel.createdAt = $createdAt")
    void linkArtistToGenre(@Param("artistId") String artistId,
                           @Param("genreId") String genreId,
                           @Param("createdAt") LocalDateTime createdAt);
}
