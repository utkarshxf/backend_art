package com.basic.JWTSecurity.artwork_server.model.projection;


import com.basic.JWTSecurity.artwork_server.model.Year;

public interface ArtworkProjection {
    String getId();
    String getDescription();
    String getName();
    String getStatus();
    String getDuration();
    String getStorageId();
    String getStorageType();
    String getType();
    Year getYear();
}
