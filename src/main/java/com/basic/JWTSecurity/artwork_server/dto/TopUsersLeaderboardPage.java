package com.basic.JWTSecurity.artwork_server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopUsersLeaderboardPage {
    private List<TopUserLeaderboardResponse> users;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalUsers;
    private Integer pageSize;
}

