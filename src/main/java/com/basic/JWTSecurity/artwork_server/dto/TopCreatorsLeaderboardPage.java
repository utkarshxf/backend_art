package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopCreatorsLeaderboardPage {
    private List<TopCreatorLeaderboardResponse> creators;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalCreators;
    private Integer pageSize;
}

