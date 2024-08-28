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
import java.util.List;

@Node("Artwork")
@Getter
@Setter
@Builder
public class Artwork {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    private String description;
    private String name;
    private Status status;
    private String imageUrl;
    private String madeWith;
    private StorageType storageType;
    private LocalDateTime releasedDate;
    private ArtType type;
    @Relationship(value = "HAS_COMMENT", direction = Relationship.Direction.INCOMING)
    private List<Comment> comments;
    @Relationship(value = "FEATURED_WITH", direction = Relationship.Direction.INCOMING)
    private List<Artist> featuredArtist;
    @Relationship(value = "RELEASED_IN")
    private Year year;
    @Relationship(value = "BELONGS",direction = Relationship.Direction.INCOMING)
    private Gallery gallery;
}
