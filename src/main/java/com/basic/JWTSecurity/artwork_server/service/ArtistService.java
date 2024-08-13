package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Artist;

public interface ArtistService {

    Artist createNew(Artist artist);
    Artist findById(String id);
    void deleteById(String id);

    void addArtistAndArtworkRelationship(String artistId, Integer releaseYear, String id, String genreId);
}
