package com.basic.JWTSecurity.artwork_server.dto;



import com.basic.JWTSecurity.artwork_server.model.ArtType;
import com.basic.JWTSecurity.artwork_server.model.StorageType;

import java.time.LocalDateTime;

public record ArtworkRecord( String title,
                             String imageUrl,
                             String imageUrlCompressed,
                             StorageType storageType,
                             String medium,
                             String artist,
                             ArtType artType,
                             String galleryId,
                             String genreId,
                             String description,
                             LocalDateTime releasedDate,
                             Integer releaseYear,
                             String dimensions,
                             String currentLocation,
                             String periodStyle,
                             String artMovement,
                             String licenseInfo,
                             String sourceUrl
) {
}
