package com.basic.JWTSecurity.artwork_server.model.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopArtistProjection {
    private String artistId;
    private String name;
    private String imageUrl;
    private Long totalLikes;
}

