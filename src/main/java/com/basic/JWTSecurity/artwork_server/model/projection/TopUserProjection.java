package com.basic.JWTSecurity.artwork_server.model.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopUserProjection {
    private String userId;
    private String name;
    private String profilePicture;
    private Long viewCount;
}

