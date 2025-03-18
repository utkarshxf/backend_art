package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.ArtType;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.StorageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;

import java.time.LocalDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetArtwork {
    private String title;
    private Status status;
    private StorageType storageType;
    private LocalDateTime releasedDate;
    private ArtType type;
    private String medium;
    private String artist;
    private String description;
    private String dimensions;
    private String current_location;
    private String period_style;
    private String art_movement;
    private String image_url_compressed;
    private String image_url;
    private String license_info;
    private String source_url;
    private Boolean liked;
}