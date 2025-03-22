package com.basic.JWTSecurity.artwork_server.model.projection;


import com.basic.JWTSecurity.artwork_server.model.ArtType;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.StorageType;
import com.basic.JWTSecurity.artwork_server.model.Year;

import java.time.LocalDateTime;

public interface ArtworkProjection {

    String getId();
    String getDescription();
    String getTitle();
    Status getStatus();
    String getMedium();
    String getImage_url();
    StorageType getStorageType();
    ArtType getType();
    Year getYear();
    String getCurrent_location();
    String getPeriod_style();
    String getArt_movement();
    String getDimensions();
    String getImage_url_compressed();
    String getLicense_info();
    String getSource_url();
    Boolean getLiked();
}
