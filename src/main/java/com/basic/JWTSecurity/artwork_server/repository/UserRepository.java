package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
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
            "            MERGE (user)-[:FOLLOWS {createdAt: $createdAt}]->(artist)")
    void userFollowArtist(@Param("userId") String userId,
                          @Param("artistId") String artistId,
                          @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artist:Artist {id: $artistId}) , (user:User {id: $userId})" +
            " MERGE (user)-[:IS_AN {createdAt: $createdAt}]->(artist)")
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
            OPTIONAL MATCH (:User)-[like:LIKES]->(artwork)
            WITH u, COUNT(like) as totalLikes
            WHERE totalLikes > 0
            RETURN COUNT(DISTINCT u) as totalCreators
            """)
    Long getTopCreatorsCount();
}
