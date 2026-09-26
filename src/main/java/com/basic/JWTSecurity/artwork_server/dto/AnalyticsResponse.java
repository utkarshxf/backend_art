package com.basic.JWTSecurity.artwork_server.dto;

import com.basic.JWTSecurity.artwork_server.model.projection.BucketCountProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private String artworkId;
    private String bucket; // day | week | month | year
    private LocalDateTime from;
    private LocalDateTime to;
    private List<BucketCountProjection> views;
    private List<BucketCountProjection> likes;
    private List<BucketCountProjection> dislikes;
}
