package com.basic.JWTSecurity.artwork_server.dto;

import java.time.LocalDate;

public record ArtistRegistrationRequestRecord(
         String id,
         String name,
         String  birth_date,
         String  death_date,
         String  nationality,
         String  notable_works,
         String  art_movement,
         String  education,
         String  awards,
         String  image_url,
         String  wikipedia_url,
         String  description
) {
}
