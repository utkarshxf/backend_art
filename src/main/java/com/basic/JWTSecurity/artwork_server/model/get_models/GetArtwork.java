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
    private String id;
    private String description;
    private String name;
    private Status status;
    private String imageUrl;
    private String madeWith;
    private StorageType storageType;
    private LocalDateTime releasedDate;
    private ArtType type;
}