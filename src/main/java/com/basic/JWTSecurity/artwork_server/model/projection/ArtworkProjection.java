package com.basic.JWTSecurity.artwork_server.model.projection;


import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.StorageType;
import com.basic.JWTSecurity.artwork_server.model.Year;

import java.time.LocalDateTime;

public interface ArtworkProjection {

    String getId();
    String getDescription();
    String getName();
    String getStatus();
    String getMadeWith();
    String getDuration();
    String getImageUrl();
    String getStorageType();
    String getType();
    Year getYear();
}
