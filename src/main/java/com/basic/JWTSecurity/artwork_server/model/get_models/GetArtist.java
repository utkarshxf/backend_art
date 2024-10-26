package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.relationship.ArtistRelationship;
import com.basic.JWTSecurity.artwork_server.model.relationship.GalleryRelationship;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Getter
@Setter
@Builder
public class GetArtist {
    @Id
    private String id;
    private String name;
    private String profilePicture;
    private Genre genre;
    private List<ArtistRelationship> artworks;
    private List<GalleryRelationship> galleries;
}