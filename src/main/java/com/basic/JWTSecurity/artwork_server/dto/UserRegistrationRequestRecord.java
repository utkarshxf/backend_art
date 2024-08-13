package com.basic.JWTSecurity.artwork_server.dto;

import java.time.LocalDate;

public record UserRegistrationRequestRecord(
        boolean artist,
        String id,
        String name,
        LocalDate dob,
        String gender,
        String language,
        String countryIso2
) {
}
