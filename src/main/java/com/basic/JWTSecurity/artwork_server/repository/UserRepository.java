package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProfileProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends Neo4jRepository<User,String> {


    @Query("MATCH (user:User {id: $userId}) RETURN user.id As id, user.name As name, user.profilePicture AS profilePicture, user.dob As dob, user.gender As gender, user.language As language, user.countryIso2 As countryIso2")
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


    @Query("MATCH (u:User {id: $userId})" +
            "OPTIONAL MATCH (u)-[:FOLLOWS]->(following:Artist)" +
            "OPTIONAL MATCH (u)<-[:FOLLOWS]-(followers:User)" +
            "OPTIONAL MATCH (u)-[:CREATED]->(artwork:Artwork)" +
            "RETURN u.id AS id," +
            "       u.name AS name," +
            "       u.profilePicture AS profilePicture," +
            "       COUNT(DISTINCT following) AS numberOfFollowing," +
            "       COUNT(DISTINCT followers) AS numberOfFollowers," +
            "       COLLECT(DISTINCT {" +
            "         id: artwork.id," +
            "         description: artwork.description," +
            "         name: artwork.name," +
            "         status: artwork.status," +
            "         madeWith: artwork.madeWith," +
            "         imageUrl: artwork.imageUrl," +
            "         storageType: artwork.storageType," +
            "         type: artwork.type," +
            "         year: artwork.year" +
            "       }) AS artworks")
    UserProfileProjection getUserById(String userId);
}
