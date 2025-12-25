package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopCreatorLeaderboardResponse {
    private String userId;
    private String name;
    private String profilePicture;
    private String artistId;
    private String artistName;
    private Long totalLikes;
    private Integer rank;
}
