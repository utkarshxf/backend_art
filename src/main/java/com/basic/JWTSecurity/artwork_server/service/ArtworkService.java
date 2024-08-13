package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;

public interface ArtworkService {

    Artwork create(ArtworkRecord artworkRecord, String artistId);
    void deleteById(String id);

    void userLikeAArtwork(String artworkId, String userId);
    void userDisLikeAArtwork(String artworkId, String userId);

}
