package com.basic.JWTSecurity.artwork_server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtworkStatsResponse {
    private String artworkId;
    private long likes;
    private long comments;
}
