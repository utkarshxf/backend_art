package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.AnalyticsResponse;
import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;

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

    Optional<List<GetArtwork>> popularArtwork(String userId, Integer skip, Integer limit);
    Optional<List<GetArtwork>> newArrivalArtwork(String userId, Integer skip, Integer limit);

    Optional<List<GetArtwork>> recommendedArtworkForToday(String userId, Integer skip, Integer limit);

    GetArtwork todayBiggestHit();
    public Optional<List<GetArtwork>> moreFromArtist(
            String artistId,
            String currentArtworkId,
            String userId
    );
    public Optional<List<GetArtwork>> similarGenreArtworks(
            String currentArtworkId,
            String userId
    );

    Optional<GetArtwork> getArtworkById(String userId , String artworkId);


    long getArtworkLikesCount(String artworkId);

    long getArtworkCommentsCount(String artworkId);

    List<UserProjection> getUsersWhoLikedArtwork(String artworkId, int skip, int limit);

    long getUsersWhoLikedArtworkCount(String artworkId);

    void userViewedArtwork(String artworkId, String userId);

    AnalyticsResponse getArtworkAnalytics(String artworkId, String bucket, java.time.LocalDateTime from, java.time.LocalDateTime to);

    void updateArtworkStatus(String artworkId, com.basic.JWTSecurity.artwork_server.model.Status status);

}
