package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;

import java.util.List;
import java.util.Optional;

public interface ArtworkService {

    Artwork create(ArtworkRecord artworkRecord, String artistId);
    void deleteById(String id);

    void userLikeAnArtwork(String artworkId, String userId);
    void userUnLikeAnArtwork(String artworkId, String userId);
    void userDislikeAnArtwork(String artworkId, String userId);
    void userUnDislikeAnArtwork(String artworkId, String userId);
    Optional<List<GetArtwork>> recommendArtwork(String userId, Integer skip, Integer limit);
    Optional<GetArtwork> getArtworkById(String userId , String artworkId);

}
