package com.basic.JWTSecurity.artwork_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistStatsResponse {
    private String artistId;
    private long followers;
    private long totalLikesOnArtworks;
    private long totalArtworks;
}
