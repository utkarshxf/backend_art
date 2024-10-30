package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.*;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends Neo4jRepository<Artist,String> {

    @Query("MATCH (artist: Artist {id: $artistId}) RETURN artist.id As id, artist.name as name , artist.profilePicture as profilePicture")
    Optional<ArtistProjection>  findByIdProjection(@Param("artistId")String id);

    @Query("MATCH (artist: Artist {id: $artistId}) " +
            "MATCH (year: Year {year: $releasedYear}) " +
            "MATCH (artwork: Artwork {id: $artworkId}) " +
            "MATCH (genre: Genre {id: $genreId}) " +
            "MERGE (artist)-[:CREATED {createdAt: $createdAt}]->(artwork) "+
            "MERGE (artwork)-[:BELONGS_TO_GENRE {createdAt: $createdAt}]->(genre) "+
            "MERGE (artwork)-[:RELEASED_IN {createdAt: $createdAt}]->(year)"
    )
    void addArtistAndArtworkRelationship(@Param("artistId") String artistId,
                                         @Param("releasedYear") Integer releasedYear,
                                         @Param("artworkId") String artworkId,
                                         @Param("createdAt") LocalDateTime localDateTime,
                                         @Param("genreId") String genreId);


    @Query("MATCH (artist:Artist) " +
            "WHERE artist.name STARTS WITH $artistName " +
            "RETURN artist.id AS id, artist.name AS name ,artist.profilePicture as profilePicture " +
            "LIMIT $responseSize")
    List<ArtistProjection> getArtist(String artistName, Integer responseSize);



    @Query("""
        MATCH (artist:Artist {id: $artistId})
                            OPTIONAL MATCH (artist)-[created:CREATED]->(artwork:Artwork)
                            RETURN artwork.id AS id,
                                 artwork.description AS description,
                                 artwork.name AS name,
                                 artwork.status AS status,
                                 artwork.imageUrl AS imageUrl,
                                 artwork.madeWith AS madeWith,
                                artwork.storageType AS storageType,
                                 artwork.releasedDate AS releasedDate,
                                artwork.type AS type
    """)
    List<GetArtwork> getArtworkByArtistId(String artistId);

    @Query("""
        MATCH (artist:Artist {id: $artistId})
                            RETURN artist.id AS id,
                                 artist.name AS name,
                                 artist.profilePicture AS profilePicture
    """)
    List<GetArtist> getArtistById(String artistId);
}
