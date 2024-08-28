package com.basic.JWTSecurity.artwork_server.model.projection;

import com.basic.JWTSecurity.artwork_server.model.Status;

public interface GalleryProjection {
    String getId();
    String getName();
    String getDescription();

    String getCoverUrl();
    Status getStatus();
}
