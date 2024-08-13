package com.basic.JWTSecurity.artwork_server.model;


import com.basic.JWTSecurity.artwork_server.model.relationship.FollowRelationship;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.util.List;

@Node("User")
@Getter
@Setter
@Builder
public class User {

    @Id
    private String id;
    private String name;
    private LocalDate dob;
    private String gender;
    private String language;
    private String countryIso2;
    @Relationship("IS_AN")
    private Artist artist;
    @Relationship("LIKES")
    private List<Genre> likedGenres;
    @Relationship("CREATED")
    private List<Favorites> favorites;
    @Relationship("LIKES")
    private List<Album> albums;
    @Relationship("POSTED_COMMENT")
    private List<Comment> comments;
    @Relationship("FOLLOWS")
    private List<FollowRelationship> artists;





}
