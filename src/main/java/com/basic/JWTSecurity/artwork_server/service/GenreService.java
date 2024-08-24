package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.projection.GenreProjection;

import java.util.List;

public interface GenreService {
    Genre create(Genre genre);
    void addArtistToGenre(String genreId, String artistId);
    void removeArtistFromGenre(String genreId, String artistId);
    void addArtworkToGenre(String genreId, String artworkId);
    void removeArtworkFromGenre(String genreId, String artworkId);

    List<GenreProjection> getGenres();
}
