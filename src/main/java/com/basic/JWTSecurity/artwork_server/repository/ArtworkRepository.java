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
                    "OPTIONAL MATCH (user:User {id: $userId})-[like:LIKES]->(artwork)" +
                    "RETURN artwork.id AS id," +
                    " artwork.name AS name, " +
                    "artwork.description AS description, " +
                    "artwork.status AS status, " +
                    "artwork.duration AS duration, " +
                    "artwork.imageUrl AS imageUrl, " +
                    "artwork.storageType AS storageType, " +
                    "artwork.type AS type, " +
                    "artwork.year AS year , " +
                    "artwork.madeWith AS madeWith, " +
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


    @Query(
            "MATCH (user:User {id: $userId}) " +
                    "CALL { " +
                    "   WITH user " +
                    "   MATCH (user)-[:LIKES]->(artwork:Artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(recommendArtwork:Artwork) " +
                    "   WHERE NOT (user)-[:LIKES]->(recommendArtwork) " +
                    "   WITH DISTINCT recommendArtwork, count(DISTINCT otherUser) AS userSimilarity " +
                    "   RETURN recommendArtwork, userSimilarity, 1 AS priority " +
                    "   UNION " +
                    "   MATCH (artwork:Artwork) " +
                    "   WHERE NOT (:User {id: $userId})-[:LIKES]->(artwork) " +
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
                    "recommendArtwork.name AS name, " +
                    "recommendArtwork.description AS description, " +
                    "recommendArtwork.status AS status, " +
                    "recommendArtwork.imageUrl AS imageUrl, " +
                    "recommendArtwork.storageType AS storageType, " +
                    "recommendArtwork.type AS type, " +
                    "recommendArtwork.year AS year, " +
                    "recommendArtwork.madeWith AS madeWith " +
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
                    artwork.name AS name,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.imageUrl AS imageUrl,
                    artwork.storageType AS storageType,
                    artwork.type AS type,
                    artwork.year AS year,
                    artwork.madeWith AS madeWith,
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
                OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
                RETURN 
                    artwork.id AS id,
                    artwork.name AS name,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.imageUrl AS imageUrl,
                    artwork.storageType AS storageType,
                    artwork.type AS type,
                    artwork.year AS year,
                    artwork.madeWith AS madeWith,
                    CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked
                ORDER BY artwork.createdAt DESC
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
                WITH user, artwork, likedArtwork
                OPTIONAL MATCH (artwork)<-[:LIKES]-(otherUser:User)-[:LIKES]->(likedArtwork)
                WITH artwork, count(DISTINCT otherUser) AS commonLikes
                OPTIONAL MATCH (user:User {id: $userId})-[userLike:LIKES]->(artwork)
                RETURN 
                    artwork.id AS id,
                    artwork.name AS name,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.imageUrl AS imageUrl,
                    artwork.storageType AS storageType,
                    artwork.type AS type,
                    artwork.year AS year,
                    artwork.madeWith AS madeWith,
                    CASE WHEN userLike IS NOT NULL THEN true ELSE false END AS liked
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
                    artwork.name AS name,
                    artwork.description AS description,
                    artwork.status AS status,
                    artwork.imageUrl AS imageUrl,
                    artwork.storageType AS storageType,
                    artwork.type AS type,
                    artwork.year AS year,
                    artwork.madeWith AS madeWith,
                    false AS liked
                ORDER BY todayLikes DESC
                LIMIT 1
            """)
    GetArtwork todayBiggestHit();
}
