package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
import com.basic.JWTSecurity.artwork_server.model.projection.AdminUserArtistProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.AdminUserArtworkProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.AdminUserCountProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.AdminUserLikeProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.TopCreatorProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.TopUserProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends Neo4jRepository<User, String> {


    @Query("MATCH (user:User {id: $userId}) RETURN user.id AS id, user.name AS name, user.profilePicture AS profilePicture, user.dob AS dob, user.gender AS gender, user.language AS language, user.countryIso2 AS countryIso2")
    Optional<UserProjection> findByIdProjection(@Param("userId") String id);

    @Query("MATCH (artist:Artist {id: $artistId})" +
            "            MATCH (user:User {id: $userId})" +
            "            MERGE (user)-[rel_follows:FOLLOWS]->(artist) ON CREATE SET rel_follows.createdAt = $createdAt")
    void userFollowArtist(@Param("userId") String userId,
                          @Param("artistId") String artistId,
                          @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artist:Artist {id: $artistId}) , (user:User {id: $userId})" +
            " MERGE (user)-[rel_is_an:IS_AN]->(artist) ON CREATE SET rel_is_an.createdAt = $createdAt")
    void addArtistAndUserRelationship(@Param("userId") String userId,
                                      @Param("artistId") String artistId,
                                      @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH(u:User {id: $userId})-[relation:FOLLOWS]->(a:Artist {id:$artistId})" +
            " DELETE relation")
    void userUnFollowArtist(@Param("userId") String userId,
                            @Param("artistId") String artistId);


    @Query("MATCH (user:User {id: $userId}) " +
            "RETURN user.id AS id, " +
            "user.name AS name, " +
            "user.profilePicture AS profilePicture, " +
            "user.dob AS dob, " +
            "user.gender AS gender, " +
            "user.language AS language, " +
            "user.countryIso2 AS countryIso2 ")
    GetUser getUserById(String userId);

    @Query("MATCH (u:User {id: $userId}) RETURN EXISTS((u)-[:IS_AN]->(:Artist)) as isArtist")
    boolean isUserIsArtistByUserId(String userId);

    // Admin users list/detail: per-account activity counts as separate plain traversals merged in
    // Java. A single query with several OPTIONAL MATCHes or pattern comprehensions is either a
    // cross-product or unsupported on the free-tier graph; these are the simplest shapes that exist.
    @Query("MATCH (u:User)-[:IS_AN]->(artist:Artist) " +
            "RETURN u.id AS id, artist.id AS artistId, artist.name AS artistName")
    List<AdminUserArtistProjection> getAdminUserArtists();

    @Query("MATCH (u:User)-[:IS_AN]->(:Artist)-[:CREATED]->(w:Artwork) " +
            "WHERE coalesce(w.status, 'APPROVED') <> 'DELETED' " +
            "RETURN u.id AS id, count(w) AS count")
    List<AdminUserCountProjection> getAdminUserArtworkCounts();

    // LIKES is labelled Artwork because users also LIKE genres and galleries
    @Query("MATCH (u:User)-[like:LIKES]->(:Artwork) RETURN u.id AS id, count(like) AS count")
    List<AdminUserCountProjection> getAdminUserLikeCounts();

    @Query("MATCH (u:User)-[:IS_AN]->(:Artist)<-[follow:FOLLOWS]-(:User) RETURN u.id AS id, count(follow) AS count")
    List<AdminUserCountProjection> getAdminUserFollowerCounts();

    @Query("MATCH (u:User)-[comment:POSTED_COMMENT]->(:Comment) RETURN u.id AS id, count(comment) AS count")
    List<AdminUserCountProjection> getAdminUserCommentCounts();

    @Query("MATCH (u:User {id: $userId})-[:IS_AN]->(:Artist)-[:CREATED]->(w:Artwork) " +
            "WHERE coalesce(w.status, 'APPROVED') <> 'DELETED' RETURN count(w)")
    long countArtworksForUser(@Param("userId") String userId);

    @Query("MATCH (u:User {id: $userId})-[like:LIKES]->(:Artwork) RETURN count(like)")
    long countLikesForUser(@Param("userId") String userId);

    @Query("MATCH (u:User {id: $userId})-[:IS_AN]->(:Artist)<-[follow:FOLLOWS]-(:User) RETURN count(follow)")
    long countFollowersForUser(@Param("userId") String userId);

    @Query("MATCH (u:User {id: $userId})-[comment:POSTED_COMMENT]->(:Comment) RETURN count(comment)")
    long countCommentsForUser(@Param("userId") String userId);

    @Query("MATCH (user:User {id: $userId})-[:IS_AN]->(artist:Artist) " +
            "RETURN artist.id AS id, artist.name AS name, artist.image_url AS image_url")
    Optional<ArtistProjection> findArtistByUserId(@Param("userId") String userId);

    @Query("MATCH (:User {id: $userId})-[:IS_AN]->(artist:Artist)-[:CREATED]->(w:Artwork) " +
            "RETURN w.id AS id, w.title AS title, w.status AS status, toString(w.releasedDate) AS releasedDate, " +
            "w.image_url_compressed AS imageUrlCompressed, size([(:User)-[:LIKES]->(w) | 1]) AS likeCount " +
            "ORDER BY likeCount DESC, w.id ASC LIMIT $limit")
    List<AdminUserArtworkProjection> getAdminUserArtworks(@Param("userId") String userId, @Param("limit") Integer limit);

    @Query("MATCH (u:User {id: $userId})-[like:LIKES]->(w:Artwork) " +
            "RETURN w.id AS id, w.title AS title, w.artist AS artistId, toString(like.createdAt) AS likedAt " +
            "ORDER BY like.createdAt DESC LIMIT $limit")
    List<AdminUserLikeProjection> getAdminUserRecentLikes(@Param("userId") String userId, @Param("limit") Integer limit);

    @Query("MATCH (user:User)-[:IS_AN]->(artist:Artist {id: $artistId}) RETURN user")
    Optional<User> findUserByArtistId(@Param("artistId") String artistId);

    @Query("""
            MATCH (u:User)-[v:VIEWED]->(a:Artwork)
            WITH u, COUNT(v) as viewCount
            ORDER BY viewCount DESC
            SKIP $skip LIMIT $limit
            RETURN u.id AS userId, u.name AS name, u.profilePicture AS profilePicture, viewCount AS viewCount
            """)
    List<TopUserProjection> getTopUsersByViewCount(@Param("skip") Integer skip, @Param("limit") Integer limit);

    @Query("""
            MATCH (u:User)-[v:VIEWED]->(:Artwork)
            RETURN COUNT(DISTINCT u) as totalUsers
            """)
    Long getTopUsersCount();

    @Query("""
            MATCH (u:User)-[:IS_AN]->(artist:Artist)-[:CREATED]->(artwork:Artwork)
            WHERE NOT coalesce(artwork.status, '') IN ['DELETED', 'BLOCKED']
            OPTIONAL MATCH (otherUser:User)-[like:LIKES]->(artwork)
            WITH u, artist, COUNT(like) as totalLikes
            WHERE totalLikes > 0
            ORDER BY totalLikes DESC
            SKIP $skip LIMIT $limit
            RETURN u.id AS userId, u.name AS name, u.profilePicture AS profilePicture, 
                   artist.id AS artistId, artist.name AS artistName, totalLikes
            """)
    List<TopCreatorProjection> getTopCreatorsByLikes(@Param("skip") Integer skip, @Param("limit") Integer limit);

    @Query("""
            MATCH (u:User)-[:IS_AN]->(artist:Artist)-[:CREATED]->(artwork:Artwork)
            WHERE NOT coalesce(artwork.status, '') IN ['DELETED', 'BLOCKED']
            OPTIONAL MATCH (:User)-[like:LIKES]->(artwork)
            WITH u, COUNT(like) as totalLikes
            WHERE totalLikes > 0
            RETURN COUNT(DISTINCT u) as totalCreators
            """)
    Long getTopCreatorsCount();
}
