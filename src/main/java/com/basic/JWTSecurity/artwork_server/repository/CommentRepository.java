package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Comment;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends Neo4jRepository<Comment,String> {


    @Query("MATCH (user:User {id: $userId})" +
            "            MATCH (comment:Comment {id: $commentId})" +
            "            MATCH (artwork:Artwork {id: $artworkId})" +
            "            MERGE (user)-[:POSTED_COMMENT]->(comment)" +
            "            MERGE (comment)-[:HAS_COMMENT]->(artwork)")
    void addCommentRelationship(@Param("commentId")String commentId,
                                @Param("artworkId")String artworkId,
                                @Param("userId")String userId);
}
