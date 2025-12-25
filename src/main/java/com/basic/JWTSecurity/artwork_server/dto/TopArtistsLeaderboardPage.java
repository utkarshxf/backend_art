package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopArtistsLeaderboardPage {
    private List<TopArtistLeaderboardResponse> artists;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalArtists;
    private Integer pageSize;
}

