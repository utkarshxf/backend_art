package com.basic.JWTSecurity.artwork_server.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.Set;

@Node("Gallery")
@Getter
@Setter
@Builder
public class Gallery {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    private String description;
    private String name;
    private String coverUrl;
    private Status status;

    @Relationship(type = "IS_IN",direction = Relationship.Direction.INCOMING)
    private Set<Artwork> artwork;

}
