package com.basic.JWTSecurity.artwork_server.model.get_models;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.time.LocalDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetComments {
    private String id;
    private String text;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean edited;
    private String userId;
    private String userName;
    private String userProfilePicture;
}
