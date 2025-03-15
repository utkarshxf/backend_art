package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.projection.GenreProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GenreRepository extends Neo4jRepository<Genre,String> {


    @Query("MATCH (genre: Genre {id: $genreId}), (artist: Artist {id: $artistId})" +
            " MERGE (artist)-[:BELONGS_TO_GENRE {createdAt: $createdAt}]->(genre)")
    void addArtistToGenre(@Param("genreId") String genreId, @Param("artistId")String artistId,
                          @Param("createdAt")LocalDateTime createdAt);
    @Query("MATCH (genre: Genre {id: $genreId}), (artwork: Artwork {id: $artworkId})" +
            " MERGE (artwork)-[:BELONGS_TO_GENRE {createdAt: $createdAt}]->(genre)")
    void addArtworkToGenre(@Param("genreId") String genreId, @Param("artworkId")String artworkId,
                           @Param("createdAt")LocalDateTime createdAt);
    @Query("MATCH (genre: Genre {id: $genreId})<-[relationship:BELONGS_TO_GENRE]-(artwork: Artwork {id: $artworkId})" +
            " DELETE relationship")
    void removeArtworkFromGenre(@Param("genreId") String genreId, @Param("artworkId")String artworkId);
    @Query("MATCH (genre: Genre {id: $genreId})<-[relationship:BELONGS_TO_GENRE]-(artist: Artist {id: $artistId})" +
            " DELETE relationship")
    void removeArtistFromGenre(@Param("genreId") String genreId, @Param("artistId")String artistId);

    @Query("MATCH (genre:Genre) " +
            "WHERE genre.name STARTS WITH $genreName " +
            "RETURN genre.id AS id, genre.name AS name, genre.key AS key " +
            "LIMIT 3")
    List<GenreProjection> getGenres(String genreName);

    @Query("MATCH (genre:Genre) " +
            "RETURN genre.id AS id, genre.name AS name, genre.key AS key ")
    List<GenreProjection> getAllGenres();


}
