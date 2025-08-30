package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.relationship.ArtistRelationship;
import com.basic.JWTSecurity.artwork_server.model.relationship.GalleryRelationship;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetArtist {
    private String id;
    private String name;
    private String  birth_date;
    private String  death_date;
    private String  nationality;
    private String  notable_works;
    private String  art_movement;
    private String  education;
    private String  awards;
    private String  image_url;
    private String  wikipedia_url;
    private String  description;
    private Boolean follow;
}