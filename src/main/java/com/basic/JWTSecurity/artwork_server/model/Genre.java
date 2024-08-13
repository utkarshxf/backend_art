package com.basic.JWTSecurity.artwork_server.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.List;

@Node("Genre")
@Getter
@Setter
@Builder
public class Genre {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    private String key;
    private String name;

    @Relationship("BELONGS_TO_GENRE")
    private List<Artwork> artworks;
    @Relationship("BELONGS_TO_GENRE")
    private List<Artist> artists;

}
