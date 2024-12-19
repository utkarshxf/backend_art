package com.basic.JWTSecurity.artwork_server.model.get_models;

import com.basic.JWTSecurity.artwork_server.model.Artist;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class GetUser {
    private String id;
    private String name;
    private String profilePicture;
    private LocalDate dob;
    private String gender;
    private String language;
    private String countryIso2;
    private Boolean follow;
}