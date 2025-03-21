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
                MATCH (artwork:Artwork)
                WHERE artwork.status = 'ACTIVE'
                OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
                WITH artwork, 
                     CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked,
                     CASE 
                         WHEN artwork.releasedDate IS NOT NULL THEN artwork.releasedDate 
                         ELSE datetime() - duration('P1000Y') 
                     END AS effectiveDate
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
                    liked
                ORDER BY effectiveDate DESC
                SKIP $skip LIMIT $limit
            """)
    Optional<List<GetArtwork>> newArrivalArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );

    @Query("""
                // Get user's liked artworks
                MATCH (user:User {id: $userId})-[:LIKES]->(likedArtwork:Artwork)
                WITH user, collect(likedArtwork.id) AS likedArtworkIds
                
                // Find artworks the user hasn't liked yet
                MATCH (artwork:Artwork)
                WHERE NOT artwork.id IN likedArtworkIds
                AND artwork.status = 'ACTIVE'
                
                // Calculate a daily seed based on date to ensure daily rotation
                WITH artwork, 
                     toInteger(timestamp() / (24 * 60 * 60 * 1000)) AS dailySeed,
                     likedArtworkIds
                
                // Get genre preferences from user's liked artworks
                OPTIONAL MATCH (user:User {id: $userId})-[:LIKES]->(:Artwork)-[:BELONGS_TO_GENRE]->(genre:Genre)<-[:BELONGS_TO_GENRE]-(artwork)
                WITH artwork, dailySeed, count(DISTINCT genre) AS genreMatchScore
                
                // Get artist preferences from user's liked artworks
                OPTIONAL MATCH (user:User {id: $userId})-[:LIKES]->(:Artwork)<-[:CREATED]-(artist:Artist)-[:CREATED]->(artwork)
                WITH artwork, dailySeed, genreMatchScore, count(DISTINCT artist) AS artistMatchScore
                
                // Calculate final score with some randomness based on the daily seed
                WITH artwork, 
                     (genreMatchScore * 3) + (artistMatchScore * 5) + abs((artwork.id + toString(dailySeed)) % 10) AS recommendationScore
                
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
                ORDER BY recommendationScore DESC
                SKIP $skip LIMIT $limit
            """)
    Optional<List<GetArtwork>> recommendedArtworkForToday(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );

    @Query("""
                CALL {
                    // First priority: Collaborative filtering recommendations
                    MATCH (user:User {id: $userId})-[:LIKES]->(userLikedArtwork:Artwork)<-[:LIKES]-(similarUser:User)
                                    -[:LIKES]->(recommendedArtwork:Artwork)
                    WHERE NOT (user)-[:LIKES]->(recommendedArtwork)
                    AND recommendedArtwork.status = 'ACTIVE'
                    WITH DISTINCT recommendedArtwork, 
                         count(DISTINCT similarUser) AS userSimilarity,
                         1 AS recommendationType
                    RETURN recommendedArtwork, userSimilarity, recommendationType
                    
                    UNION
                    
                    // Second priority: Popular artworks the user hasn't seen
                    MATCH (artwork:Artwork)
                    WHERE artwork.status = 'ACTIVE'
                    AND NOT (:User {id: $userId})-[:LIKES]->(artwork)
                    AND NOT (:User {id: $userId})-[:DISLIKES]->(artwork)
                    OPTIONAL MATCH (artwork)<-[:LIKES]-(liker:User)
                    WITH artwork AS recommendedArtwork, 
                         count(liker) AS userSimilarity,
                         2 AS recommendationType
                    RETURN recommendedArtwork, userSimilarity, recommendationType
                }
                
                WITH recommendedArtwork, userSimilarity, recommendationType
                ORDER BY recommendationType, userSimilarity DESC
                SKIP $skip LIMIT $limit
                
                RETURN 
                    recommendedArtwork.id AS id,
                    recommendedArtwork.title AS title,
                    recommendedArtwork.description AS description,
                    recommendedArtwork.status AS status,
                    recommendedArtwork.storageType AS storageType,
                    recommendedArtwork.releasedDate AS releasedDate,
                    recommendedArtwork.type AS type,
                    recommendedArtwork.medium AS medium,
                    recommendedArtwork.dimensions AS dimensions,
                    recommendedArtwork.artist AS artist,
                    recommendedArtwork.current_location AS current_location,
                    recommendedArtwork.period_style AS period_style,
                    recommendedArtwork.art_movement AS art_movement,
                    recommendedArtwork.image_url_compressed AS image_url_compressed,
                    recommendedArtwork.image_url AS image_url,
                    recommendedArtwork.license_info AS license_info,
                    recommendedArtwork.source_url AS source_url,
                    false AS liked
            """)
    Optional<List<GetArtwork>> recommendArtwork(
            @Param("userId") String userId,
            @Param("skip") Integer skip,
            @Param("limit") Integer limit
    );
}
