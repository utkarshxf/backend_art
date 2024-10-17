package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;

import java.util.List;

public interface ArtistService {

    Artist createNew(Artist artist);

    List<ArtistProjection> getAllArtist(String artistName, Integer responseSize);
    Artist findById(String id);
    void deleteById(String id);

    void addArtistAndArtworkRelationship(String artistId, Integer releaseYear, String id, String genreId);
}
