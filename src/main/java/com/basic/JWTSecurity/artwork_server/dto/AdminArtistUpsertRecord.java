package com.basic.JWTSecurity.artwork_server.dto;

// Admin upsert for catalogue artists (museum/historical). Unlike the public POST /artist flow this never
// creates or links a User persona; every field is stored verbatim so provenance (wikipedia_url, description)
// stays honest. genreId is optional and only linked when it exists.
public record AdminArtistUpsertRecord(
        String id,
        String name,
        String birth_date,
        String death_date,
        String nationality,
        String notable_works,
        String art_movement,
        String education,
        String awards,
        String image_url,
        String wikipedia_url,
        String description,
        String genreId
) {
}
