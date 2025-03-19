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

    @Query("MATCH (artist: Artist {id: $artistId}) RETURN artist.id As id, artist.name as name , artist.image_url as image_url")
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
            "RETURN artist.id AS id, artist.name AS name, artist.image_url AS image_url " +
            "LIMIT $responseSize")
    List<ArtistProjection> getArtist(String artistName, Integer responseSize);



    @Query("""
    MATCH (artist:Artist {id: $artistId})
    OPTIONAL MATCH (artist)-[created:CREATED]->(artwork:Artwork)
    OPTIONAL MATCH (user:User {id: $userId})-[like:LIKES]->(artwork)
    RETURN artwork.id AS id,
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
                    CASE WHEN like IS NOT NULL THEN true ELSE false END AS liked
""")
    List<GetArtwork> getArtworkByArtistId(String userId , String artistId);

    @Query("""
    MATCH (artist:Artist {id: $artistId})
    OPTIONAL MATCH (currentUser:User {id: $currentUserId})-[follow:FOLLOWS]->(artist)
    RETURN artist.id AS id,
           artist.name AS name,
           artist.birth_date AS birth_date,
           artist.death_date AS death_date,
           artist.nationality AS nationality,
           artist.notable_works AS notable_works,
           artist.art_movement AS art_movement,
           artist.education AS education,
           artist.awards AS awards,
           artist.image_url AS image_url,
           artist.wikipedia_url AS wikipedia_url,
           artist.description AS description,
           CASE WHEN follow IS NOT NULL THEN true ELSE false END AS follow
""")
    GetArtist getArtistById(String currentUserId , String artistId);
}
