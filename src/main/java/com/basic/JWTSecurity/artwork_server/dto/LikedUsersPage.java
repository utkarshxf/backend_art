package com.basic.JWTSecurity.artwork_server.dto;

import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikedUsersPage {
    private String artworkId;
    private int skip;
    private int limit;
    private long total;
    private List<UserProjection> users;
}
