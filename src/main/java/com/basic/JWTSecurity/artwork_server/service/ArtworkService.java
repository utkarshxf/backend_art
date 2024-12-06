package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.DetailedArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;

import java.util.List;
import java.util.Optional;

public interface ArtworkService {

    Artwork create(ArtworkRecord artworkRecord, String artistId);
    void deleteById(String id);

    void userLikeAnArtwork(String artworkId, String userId);
    void userDisLikeAArtwork(String artworkId, String userId);

    Optional<List<ArtworkProjection>> recommendArtwork(String userId, Integer skip, Integer limit);

    Optional<ArtworkProjection> getArtworkById(String artworkId);
}
