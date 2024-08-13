package com.basic.JWTSecurity.artwork_server.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.time.LocalDateTime;

@Node("Comment")
@Getter
@Setter
@Builder
public class Comment {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    private String text;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean edited;
    @Relationship(type = "POSTED_COMMENT",direction = Relationship.Direction.INCOMING)
    private User user;
    @Relationship(type = "HAS_COMMENT")
    private Artwork artwork;

}
