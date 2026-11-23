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

    // Targeted partial artist update: a plain Cypher SET instead of the SDN
    // entity load+save. The entity round-trip 500s on some richly-connected
    // nodes (e.g. Rodin, De Blaas); this updates only the supplied fields.
    @Query("MATCH (artist:Artist {id: $artistId}) RETURN count(artist) > 0")
    boolean artistExists(@Param("artistId") String artistId);

    @Query("MATCH (artist:Artist {id: $artistId}) " +
            "SET artist.name = coalesce($name, artist.name), " +
            "artist.birth_date = coalesce($birthDate, artist.birth_date), " +
            "artist.death_date = coalesce($deathDate, artist.death_date), " +
            "artist.nationality = coalesce($nationality, artist.nationality), " +
            "artist.art_movement = coalesce($artMovement, artist.art_movement), " +
            "artist.notable_works = coalesce($notableWorks, artist.notable_works), " +
            "artist.wikipedia_url = coalesce($wikipediaUrl, artist.wikipedia_url), " +
            "artist.description = coalesce($description, artist.description)")
    void updateArtistFields(@Param("artistId") String artistId,
                            @Param("name") String name,
                            @Param("birthDate") String birthDate,
                            @Param("deathDate") String deathDate,
                            @Param("nationality") String nationality,
                            @Param("artMovement") String artMovement,
                            @Param("notableWorks") String notableWorks,
                            @Param("wikipediaUrl") String wikipediaUrl,
                            @Param("description") String description);

    // Geo feed bulk write (v2.8.4): feed_country ISO2 (null clears) + feed_exclude flag.
    @Query("UNWIND $entries AS e " +
            "MATCH (artist:Artist {id: e.artistId}) " +
            "SET artist.feed_country = e.feedCountry, artist.feed_exclude = coalesce(e.feedExclude, false) " +
            "RETURN count(artist)")
    long setFeedCountries(@Param("entries") java.util.List<java.util.Map<String, Object>> entries);
}
