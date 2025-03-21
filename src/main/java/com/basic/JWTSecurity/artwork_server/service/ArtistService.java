package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;

import java.util.List;

public interface ArtistService {

    Artist createNew(Artist artist);

    List<ArtistProjection> getAllArtist(String artistName, Integer responseSize);

    Artist findById(String id);

    void deleteById(String id);

    void addArtistAndArtworkRelationship(String artistId, Integer releaseYear, String id, String genreId);

    List<GetArtwork> getArtworkByUserID(String userId , String artistId);

    GetArtist getArtistByArtistID(String currentUserId , String artistId);
    GetArtist getArtistByArtworkID(String currentUserId , String artworkId);


}
