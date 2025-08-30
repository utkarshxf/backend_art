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
    private String title;
    private Status status;
    private String artist;
    private StorageType storageType;
    private LocalDateTime releasedDate;
    private ArtType type;
    private String medium;
    private String description;
    private String dimensions;
    private String current_location;
    private String period_style;
    private String art_movement;
    private String image_url_compressed;
    private String image_url;
    private String license_info;
    private String source_url;

    @Relationship(value = "HAS_COMMENT", direction = Relationship.Direction.INCOMING)
    private List<Comment> comments;
    @Relationship(value = "FEATURED_WITH", direction = Relationship.Direction.INCOMING)
    private List<Artist> featuredArtist;
    @Relationship(value = "RELEASED_IN")
    private Year year;
    @Relationship(value = "BELONGS",direction = Relationship.Direction.INCOMING)
    private Gallery gallery;
}
