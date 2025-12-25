package com.basic.JWTSecurity.artwork_server.model.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopCreatorProjection {
    private String userId;
    private String name;
    private String profilePicture;
    private String artistId;
    private String artistName;
    private Long totalLikes;
}

