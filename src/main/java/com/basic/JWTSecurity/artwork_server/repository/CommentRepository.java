package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetComments;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends Neo4jRepository<Comment,String> {


    @Query("MATCH (user:User {id: $userId})" +
            "            MATCH (comment:Comment {id: $commentId})" +
            "            MATCH (artwork:Artwork {id: $artworkId})" +
            "            MERGE (user)-[:POSTED_COMMENT]->(comment)" +
            "            MERGE (comment)-[:HAS_COMMENT]->(artwork)")
    void addCommentRelationship(@Param("commentId")String commentId,
                                @Param("artworkId")String artworkId,
                                @Param("userId")String userId);

    @Query("MATCH (artwork:Artwork {id: $artworkId})<-[:HAS_COMMENT]-(comment:Comment)<-[:POSTED_COMMENT]-(user:User) " +
            "RETURN comment.id as id, " +
            "comment.text as text, " +
            "comment.createdAt as createdAt, " +
            "comment.updatedAt as updatedAt, " +
            "comment.edited as edited, " +
            "user.id as userId, " +
            "user.name as userName, " +
            "user.profilePicture as userProfilePicture " +
            "ORDER BY comment.createdAt DESC")
    List<GetComments> getArtworkComments(@Param("artworkId") String artworkId);
}
