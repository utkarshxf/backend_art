package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DetailedArtwork {
    private ArtworkProjection artwork;
    private Integer userSimilarity;
    private Integer likes;
    private Integer noOfComments;
    private String artworkGenre;
    private String artistName;
    private String artistId;
}