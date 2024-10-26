package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.RecommendedArtwork;

import java.util.List;

public interface ArtworkService {

    Artwork create(ArtworkRecord artworkRecord, String artistId);
    void deleteById(String id);

    void userLikeAnArtwork(String artworkId, String userId);
    void userDisLikeAArtwork(String artworkId, String userId);

    List<RecommendedArtwork> recommendArtwork(String userId, Integer skip, Integer limit);
}
