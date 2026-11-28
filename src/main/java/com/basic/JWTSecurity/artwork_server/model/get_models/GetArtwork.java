package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.ArtType;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.StorageType;
import com.basic.JWTSecurity.artwork_server.model.rights.RightsStatus;
import com.basic.JWTSecurity.artwork_server.service.RightsPolicy;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * Normalized rights status, computed server-side from license_info/source_url.
     * Clients must use these derived fields instead of parsing license_info.
     */
    @JsonProperty("rights_status")
    public RightsStatus getRightsStatus() {
        return RightsPolicy.normalize(license_info, source_url);
    }

    /** True only for public-domain/CC0 works: the client may offer saving the high-res image. */
    @JsonProperty("can_save")
    public boolean getCanSave() {
        return RightsPolicy.canSave(getRightsStatus());
    }

    /** True only for public-domain/CC0 works: the client may offer setting the artwork as wallpaper. */
    @JsonProperty("can_set_wallpaper")
    public boolean getCanSetWallpaper() {
        return RightsPolicy.canSetWallpaper(getRightsStatus());
    }
}