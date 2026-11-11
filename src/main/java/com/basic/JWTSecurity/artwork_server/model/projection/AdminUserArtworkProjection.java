package com.basic.JWTSecurity.artwork_server.model.projection;

public interface AdminUserArtworkProjection {
    String getId();
    String getTitle();
    String getStatus();
    String getReleasedDate();
    String getImageUrlCompressed();
    Long getLikeCount();
}
