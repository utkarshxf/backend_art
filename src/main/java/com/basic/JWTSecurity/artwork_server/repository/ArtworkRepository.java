package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArtworkRepository extends Neo4jRepository<Artwork, String> {

    @Query(
            "MATCH (artwork:Artwork {id: $artworkId}) " +
                    "OPTIONAL MATCH (user:User {id: $userId})-[like:LIKES]->(artwork) " +
                    "RETURN artwork.id AS id, " +
                    "artwork.title AS title, " +
                    "artwork.description AS description, " +
                    "artwork.status AS status, " +
                    "artwork.storageType AS storageType, " +
                    "artwork.releasedDate AS releasedDate, " +
                    "artwork.type AS type, " +
                    "artwork.medium AS medium, " +
                    "artwork.dimensions AS dimensions, " +
                    "artwork.artist AS artist, " +
                    "artwork.current_location AS current_location, " +
                    "artwork.period_style AS period_style, " +
                    "artwork.art_movement AS art_movement, " +
                    "artwork.image_url_compressed AS image_url_compressed, " +
                    "artwork.image_url AS image_url, " +
                    "artwork.license_info AS license_info, " +
                    "artwork.source_url AS source_url, " +
                    "CASE WHEN like IS NOT NULL THEN true ELSE false END AS liked"
    )
    Optional<GetArtwork> findByIdProjection(@Param("userId") String userId, @Param("artworkId") String id);

    @Query("MATCH (user: User {id: $userId}) " +
            "MATCH (artwork: Artwork {id: $artworkId}) " +
            "MERGE (user)-[r:LIKES]->(artwork) " +
            "ON CREATE SET r.createdAt = $createdAt " +
            "RETURN r")
    void userLikeAnArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId, @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (user: User {id: $userId}) " +
            "MATCH (artwork: Artwork {id: $artworkId}) " +
            "MERGE (user)-[r:DISLIKES]->(artwork) " +
            "ON CREATE SET r.createdAt = $createdAt " +
            "RETURN r")
    void userDislikeAnArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId, @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artwork:Artwork {id: $artworkId})<-[relationship:LIKES]- (user: User {id : $userId})" +
            " DELETE relationship")
    void userUnLikeAArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId);

    @Query("MATCH (artwork:Artwork {id: $artworkId})<-[relationship:DISLIKES]- (user: User {id : $userId})" +
            " DELETE relationship")
    void userUnDislikeAArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId);

    @Query("""
                MATCH (user:User {id: $userId})-[r:LIKES]->(artwork:Artwork {id: $artworkId})
                RETURN count(r) > 0
            """)
    boolean checkLikeExists(@Param("artworkId") String artworkId, @Param("userId") String userId);

    @Query("""
                MATCH (user:User {id: $userId})-[r:DISLIKES]->(artwork:Artwork {id: $artworkId})
                RETURN count(r) > 0
            """)
    boolean checkDislikeExists(@Param("artworkId") String artworkId, @Param("userId") String userId);

    // Counts and stats
    @Query("""
            MATCH (a:Artwork {id: $artworkId})
            OPTIONAL MATCH (a)<-[:LIKES]-(:User)
            RETURN count(*) as likes
            """)
    long getArtworkLikesCount(@Param("artworkId") String artworkId);

    @Query("""
            MATCH (a:Artwork {id: $artworkId})
            OPTIONAL MATCH (a)<-[:HAS_COMMENT]-(:Comment)
            RETURN count(*) as comments
            """)
    long getArtworkCommentsCount(@Param("artworkId") String artworkId);

    // Users who liked an artwork (paged)
    @Query("""
            MATCH (u:User)-[:LIKES]->(a:Artwork {id: $artworkId})
            RETURN u.id as id, u.name as name, u.profilePicture as profilePicture, u.dob as dob, u.gender as gender, u.language as language, u.countryIso2 as countryIso2
            SKIP $skip LIMIT $limit
            """)
    List<com.basic.JWTSecurity.artwork_server.model.projection.UserProjection> getUsersWhoLikedArtwork(@Param("artworkId") String artworkId,
                                                                                                        @Param("skip") int skip,
                                                                                                        @Param("limit") int limit);

    @Query("""
            MATCH (:User)-[:LIKES]->(a:Artwork {id: $artworkId})
            RETURN count(*)
            """)
    long getUsersWhoLikedArtworkCount(@Param("artworkId") String artworkId);

    // Views
    @Query("""
            MATCH (u:User {id:$userId})
            MATCH (a:Artwork {id:$artworkId})
            MERGE (u)-[r:VIEWED]->(a)
            ON CREATE SET r.createdAt = $createdAt
            ON MATCH SET r.lastSeenAt = $createdAt
            RETURN r
            """)
    void userViewedArtwork(@Param("artworkId") String artworkId, @Param("userId") String userId, @Param("createdAt") java.time.LocalDateTime createdAt);

    // Analytics by bucket for a given relation type
    @Query("""
            MATCH (u:User)-[r]->(a:Artwork {id: $artworkId})
            WHERE type(r) = $relationType AND r.createdAt >= $from AND r.createdAt <= $to
            WITH r,
                 CASE $bucket
                   WHEN 'day' THEN date(r.createdAt)
                   WHEN 'week' THEN date.truncate('week', r.createdAt)
                   WHEN 'month' THEN date.truncate('month', r.createdAt)
                   WHEN 'year' THEN date.truncate('year', r.createdAt)
                 END AS period
            RETURN period AS period, count(r) AS count
            ORDER BY period ASC
            """)
    java.util.List<com.basic.JWTSecurity.artwork_server.model.projection.BucketCountProjection> getArtworkAnalyticsBuckets(
            @Param("artworkId") String artworkId,
            @Param("relationType") String relationType,
            @Param("bucket") String bucket,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    // Update status
    @Query("""
            MATCH (a:Artwork {id:$artworkId})
            SET a.status = $status
            RETURN a.id
            """)
    String updateArtworkStatus(@Param("artworkId") String artworkId, @Param("status") String status);


//    @Query(
//            "MATCH (user:User {id: $userId}) " +
//                    "CALL { " +
//                    "WITH user " +
//                    "MATCH (user)-[:LIKES]->(artwork:Artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(recommendArtwork:Artwork) " +
//                    "WHERE NOT (user)-[:LIKES]->(recommendArtwork) " +
//                    "WITH DISTINCT recommendArtwork, count(DISTINCT otherUser) AS userSimilarity " +
//                    "RETURN recommendArtwork, userSimilarity, 1 AS priority " +
//                    "UNION " +
//                    "MATCH (artwork:Artwork) " +
//                    "WHERE NOT (:User {id: $userId})-[:LIKES]->(artwork) " +
//                    "AND NOT ((:User {id: $userId})-[:LIKES]->(:Artwork)<-[:LIKES]-(:User)-[:LIKES]->(artwork)) " +
//                    "RETURN artwork AS recommendArtwork, 0 AS userSimilarity, 2 AS priority " +
//                    "} " +
//                    "WITH recommendArtwork, userSimilarity, priority " +
//                    "OPTIONAL MATCH (recommendArtwork)<-[:LIKES]-(likeUser:User) " +
//                    "WITH recommendArtwork, userSimilarity, priority, count(DISTINCT likeUser) AS likes " +
//                    "OPTIONAL MATCH (recommendArtwork)<-[:HAS_COMMENT]-(comment:Comment) " +
//                    "WITH recommendArtwork, userSimilarity, priority, likes, count(DISTINCT comment) AS noOfComments " +
//                    "OPTIONAL MATCH (recommendArtwork)<-[:CREATED]-(artist:Artist) " +
//                    "OPTIONAL MATCH (recommendArtwork)-[:BELONGS_TO_GENRE]->(artworkGenre:Genre) " +
//                    "RETURN recommendArtwork AS artwork, userSimilarity, likes, noOfComments,artworkGenre.name AS artworkGenre, artist.name AS artistName, artist.id AS artistId " +
//                    "ORDER BY priority, userSimilarity DESC, likes DESC " +
//                    "SKIP $skip LIMIT $limit"
//    )
//    List<DetailedArtwork> recommendArtwork(
//            @Param("userId") String userId,
//            @Param("skip") Integer skip,
//            @Param("limit") Integer limit
//    );


    @Query(
            "MATCH (user:User {id: $userId}) " +
                    "CALL { " +
                    "   WITH user " +
                    "   MATCH (user)-[:LIKES]->(artwork:Artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(recommendArtwork:Artwork) " +
                    "   WHERE NOT (user)-[:LIKES]->(recommendArtwork) " +
                    "   AND NOT (user)-[:DISLIKES]->(recommendArtwork) " +
                    "   WITH DISTINCT recommendArtwork, count(DISTINCT otherUser) AS userSimilarity " +
                    "   RETURN recommendArtwork, userSimilarity, 1 AS priority " +
                    "   UNION " +
                    "   MATCH (artwork:Artwork) " +
                    "   WHERE NOT (:User {id: $userId})-[:LIKES]->(artwork) " +
                    "   AND NOT (:User {id: $userId})-[:DISLIKES]->(artwork) " +
                    "   AND NOT ((:User {id: $userId})-[:LIKES]->(:Artwork)<-[:LIKES]-(:User)-[:LIKES]->(artwork)) " +
                    "   RETURN artwork AS recommendArtwork, 0 AS userSimilarity, 2 AS priority " +
                    "} " +
                    "WITH recommendArtwork, userSimilarity, priority " +
                    "OPTIONAL MATCH (recommendArtwork)<-[like:LIKES]-(likeUser:User) " +
                    "WITH recommendArtwork, userSimilarity, priority, count(DISTINCT likeUser) AS likes " +
                    "OPTIONAL MATCH (recommendArtwork)<-[:HAS_COMMENT]-(comment:Comment) " +
                    "WITH recommendArtwork, userSimilarity, priority, likes, count(DISTINCT comment) AS noOfComments " +
                    "OPTIONAL MATCH (recommendArtwork)<-[:CREATED]-(artist:Artist) " +
                    "OPTIONAL MATCH (recommendArtwork)-[:BELONGS_TO_GENRE]->(artworkGenre:Genre) " +
                    "RETURN " +
                    "recommendArtwork.id AS id, " +
                    "recommendArtwork.title AS title, " +
                    "recommendArtwork.description AS description, " +
                    "recommendArtwork.status AS status, " +
                    "recommendArtwork.storageType AS storageType, " +
                    "recommendArtwork.releasedDate AS releasedDate, " +
                    "recommendArtwork.type AS type, " +
                    "recommendArtwork.medium AS medium, " +
                    "recommendArtwork.dimensions AS dimensions, " +
                    "recommendArtwork.artist AS artist, " +
                    "recommendArtwork.current_location AS current_location, " +
                    "recommendArtwork.period_style AS period_style, " +
                    "recommendArtwork.art_movement AS art_movement, " +
                    "recommendArtwork.image_url_compressed AS image_url_compressed, " +
                    "recommendArtwork.image_url AS image_url, " +
                    "recommendArtwork.license_info AS license_info, " +
                    "recommendArtwork.source_url AS source_url, " +
                    "false AS liked " +
                    "ORDER BY priority, userSimilarity DESC, likes DESC " +
                    "SKIP $skip LIMIT $limit"
    )
    Optional<List<GetArtwork>> recommendArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );

    @Query("""
                MATCH (artwork:Artwork)
                OPTIONAL MATCH (artwork)<-[like:LIKES]-(likeUser:User)
                OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
                WITH artwork, count(DISTINCT like) AS likes, 
                     CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked
                OPTIONAL MATCH (artwork)<-[:HAS_COMMENT]-(comment:Comment)
                WITH artwork, likes, liked, count(DISTINCT comment) AS comments
                RETURN 
                    artwork.id AS id,
                    artwork.title AS title,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.artist AS artist,
                    artwork.storageType AS storageType,
                    artwork.releasedDate AS releasedDate,
                    artwork.type AS type,
                    artwork.medium AS medium,
                    artwork.dimensions AS dimensions,
                    artwork.current_location AS current_location,
                    artwork.period_style AS period_style,
                    artwork.art_movement AS art_movement,
                    artwork.image_url_compressed AS image_url_compressed,
                    artwork.image_url AS image_url,
                    artwork.license_info AS license_info,
                    artwork.source_url AS source_url,
                    liked
                ORDER BY likes DESC, comments DESC
                SKIP $skip LIMIT $limit
            """)
    Optional<List<GetArtwork>> popularArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );

    @Query("""
        MATCH (artwork:Artwork)
        OPTIONAL MATCH (artist:Artist)-[created:CREATED]->(artwork)
        OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
        OPTIONAL MATCH (user:User {id: $userId})-[userDislike:DISLIKES]->(artwork)
        WHERE userDislike IS NULL
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
        ORDER BY created.createdAt DESC
        SKIP $skip LIMIT $limit
    """)
    Optional<List<GetArtwork>> newArrivalArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );
    @Query("""
                MATCH (user:User {id: $userId})-[:LIKES]->(likedArtwork:Artwork)
                MATCH (artwork:Artwork)
                WHERE artwork <> likedArtwork
                  AND NOT EXISTS {
                    MATCH (user)-[:LIKES]->(artwork)
                  }
                WITH DISTINCT artwork, user
                OPTIONAL MATCH (artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(likedArtwork:Artwork)<-[:LIKES]-(user)
                WITH artwork, user, COUNT(otherUser) AS commonLikes
                RETURN\s
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
                    false AS liked
                ORDER BY commonLikes DESC
                SKIP $skip LIMIT $limit
            """)
    Optional<List<GetArtwork>> recommendedArtworkForToday(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );

    @Query("""
                MATCH (artwork:Artwork)
                OPTIONAL MATCH (artwork)<-[like:LIKES]-(user:User)
                WHERE date(like.createdAt) = date()
                WITH artwork, count(DISTINCT like) AS todayLikes
                WHERE todayLikes > 0
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
                    false AS liked
                ORDER BY todayLikes DESC
                LIMIT 1
            """)
    GetArtwork todayBiggestHit();


    @Query("""
    MATCH (artist:Artist {id: $artistId})-[:CREATED]->(artwork:Artwork)
    WHERE artwork.id <> $currentArtworkId
    OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
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
    ORDER BY rand()
    LIMIT 4
""")
    Optional<List<GetArtwork>> moreFromArtist(
            @Param("artistId") String artistId,
            @Param("currentArtworkId") String currentArtworkId,
            @Param("userId") String userId
    );

    @Query("""
    MATCH (sourceArtwork:Artwork {id: $artworkId})-[:BELONGS_TO_GENRE]->(genre:Genre)
    MATCH (genre)<-[:BELONGS_TO_GENRE]-(similarArtwork:Artwork)
    WHERE similarArtwork.id <> $artworkId
    WITH DISTINCT similarArtwork, COUNT(genre) AS commonGenres
    OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(similarArtwork)
    OPTIONAL MATCH (user:User {id: $userId})-[userDislike:DISLIKES]->(similarArtwork)
    WHERE userDislike IS NULL
    RETURN 
        similarArtwork.id AS id,
        similarArtwork.title AS title,
        similarArtwork.description AS description,
        similarArtwork.status AS status,
        similarArtwork.storageType AS storageType,
        similarArtwork.releasedDate AS releasedDate,
        similarArtwork.type AS type,
        similarArtwork.medium AS medium,
        similarArtwork.dimensions AS dimensions,
        similarArtwork.artist AS artist,
        similarArtwork.current_location AS current_location,
        similarArtwork.period_style AS period_style,
        similarArtwork.art_movement AS art_movement,
        similarArtwork.image_url_compressed AS image_url_compressed,
        similarArtwork.image_url AS image_url,
        similarArtwork.license_info AS license_info,
        similarArtwork.source_url AS source_url,
        CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked
    ORDER BY commonGenres DESC, rand()
    LIMIT 4
""")
    Optional<List<GetArtwork>> similarGenreArtworks(
            @Param("artworkId") String artworkId,
            @Param("userId") String userId
    );

}
