package com.basic.JWTSecurity.artwork_server.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendedArtwork {
    private Artwork artwork;
    private Integer userSimilarity;
    private Integer likes;
    private Integer noOfComments;
    private String artworkGenre;
    private String artistName;
    private String artistId;
}