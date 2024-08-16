package com.basic.JWTSecurity.artwork_server.dto;



import com.basic.JWTSecurity.artwork_server.model.ArtType;
import com.basic.JWTSecurity.artwork_server.model.StorageType;

import java.time.LocalDateTime;

public record ArtworkRecord(String title,
                            String imageUrl,
                            StorageType storageType,
                            String madeWith,
                            ArtType artType,
                            String albumId,
                            String genreId,
                            long duration,
                            LocalDateTime releasedDate,
                            Integer releaseYear) {
}
