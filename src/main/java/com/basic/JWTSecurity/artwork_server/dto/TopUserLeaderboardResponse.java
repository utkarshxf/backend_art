package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopUserLeaderboardResponse {
    private String userId;
    private String name;
    private String profilePicture;
    private Long viewCount;
    private Integer rank;
}

