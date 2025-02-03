package com.basic.JWTSecurity.artwork_server.model.get_models;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetFavorites {
    private String id;
    private String title;
    private String description;
}
