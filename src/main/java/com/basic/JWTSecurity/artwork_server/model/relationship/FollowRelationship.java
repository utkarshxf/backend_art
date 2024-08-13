package com.basic.JWTSecurity.artwork_server.model.relationship;


import com.basic.JWTSecurity.artwork_server.model.Artist;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;

@RelationshipProperties
@Getter
@Setter
public class FollowRelationship {

    @Id
    @GeneratedValue
    private Long id;
    @TargetNode
    private Artist artist;
    private LocalDateTime createdAt;
}