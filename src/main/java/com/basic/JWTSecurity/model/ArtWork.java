package com.basic.JWTSecurity.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "artwork")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtWork {
    @Id
    private String artworkId;
    private String artistId;
    private String artist;
    private String name;
    private String imageUrl;
}
