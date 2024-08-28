package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.projection.GalleryProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GalleryRepository extends Neo4jRepository<Gallery,String> {

    @Query("MATCH (user:User {id: $userId})" +
            "            MATCH (gallery:Gallery {id: $galleryId})" +
            "            MERGE (user)-[:LIKES {createdAt: $createdAt}]->(gallery)")
    void userLikesGallery(@Param("userId")String userId,
                          @Param("galleryId") String galleryId,
                          @Param("createdAt") LocalDateTime createdAt);


    @Query("MATCH (user:User {id: $userId}) -[relationship:LIKES]-> (gallery:Gallery {id: $galleryId})" +
            " DELETE relationship")
    void userDislikeGallery(@Param("userId")String userId,
                            @Param("galleryId") String galleryId);


    @Query("MATCH (artist:Artist {id: $artistId}), (gallery:Gallery {id: $galleryId}), (year:Year {year: $year})" +
            " MERGE (gallery)-[:RELEASED_IN {created: $createdAt}]->(year)" +
            " MERGE (artist)-[:CREATED {created: $createdAt}]->(gallery)")
    void addReleasedYearAndArtist(@Param("artistId")String artistId,
                        @Param("galleryId") String galleryId,
                        @Param("year") Integer year,
                        @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (gallery:Gallery) " +
            "WHERE gallery.name STARTS WITH $galleryName " +
            "RETURN gallery.id AS id, gallery.name AS name " +
            "LIMIT 3")
    List<GalleryProjection> getGallery(String galleryName);
}
