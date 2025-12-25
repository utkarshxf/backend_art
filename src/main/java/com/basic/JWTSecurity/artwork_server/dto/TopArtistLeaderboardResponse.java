package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopArtistLeaderboardResponse {
    private String artistId;
    private String name;
    private String imageUrl;
    private Long totalLikes;
    private Integer rank;
}



