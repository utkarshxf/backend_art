package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends Neo4jRepository<User,String> {


    @Query("MATCH (user:User {id: $userId}) RETURN user.id As id, user.name As name, user.dob As dob, user.gender As gender, user.language As language, user.countryIso2 As countryIso2")
    Optional<UserProjection> findByIdProjection(@Param("userId") String id);

    @Query("MATCH (artist:Artist {id: $artistId})" +
            "            MATCH (user:User {id: $userId})" +
            "            MERGE (user)-[:FOLLOWS {createdAt: $createdAt}]->(artist)")
    void userFollowArtist(@Param("userId")String userId,
                          @Param("artistId") String artistId,
                          @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH (artist:Artist {id: $artistId}) , (user:User {id: $userId})" +
            " MERGE (user)-[:IS_AN {createdAt: $createdAt}]->(artist)")
    void addArtistAndUserRelationship(@Param("userId")String userId,
                          @Param("artistId") String artistId,
                          @Param("createdAt") LocalDateTime createdAt);

    @Query("MATCH(u:User {id: $userId})-[relation:FOLLOWS]->(a:Artist {id:$artistId})" +
            " DELETE relation")
    void userUnFollowArtist(@Param("userId")String userId,
                          @Param("artistId") String artistId);



}
