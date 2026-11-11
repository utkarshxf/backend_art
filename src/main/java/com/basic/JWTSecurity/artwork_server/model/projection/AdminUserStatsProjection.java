package com.basic.JWTSecurity.artwork_server.model.projection;

public interface AdminUserStatsProjection {
    String getId();
    Boolean getIsArtist();
    String getArtistId();
    String getArtistName();
    Long getArtworkCount();
    Long getLikeCount();
    Long getFollowerCount();
    Long getCommentCount();
}
