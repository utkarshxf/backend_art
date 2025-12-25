package com.basic.JWTSecurity.artwork_server.model.projection;


import org.springframework.beans.factory.annotation.Value;

public interface ArtistProjection {
    String getId();
    String getName();

    @Value("#{target.image_url}")
    String getImageUrl();
}
