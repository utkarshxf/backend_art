package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.geo.GeoCountries;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class ArtworkServiceImpl implements ArtworkService {

    private final ArtworkRepository repository;
    private final YearService yearService;
    private final ArtistService artistService;
    @Override
    public Artwork create(ArtworkRecord artworkRecord, String artistId) {

        String artistId1 = artworkRecord.artist();
        Integer year = artworkRecord.releaseYear();
        String genreId = artworkRecord.genreId();

        if (genreId == null) {
            genreId = "6311ae17"; // Assign a default genre ID if none is provided
        }

        if (year == null) {
            year = LocalDateTime.now().getYear();
        }

        if(artistId1==null ){
            artistId1=artistId;
        }
        if(artistId ==null ){
            artistId=artistId1;
        }

        if(artworkRecord.genreId() == null){
            throw new RuntimeException("Genre ID must be provided to create an artwork");
        }

        Artwork artwork = Artwork.builder()
                .title(artworkRecord.title())
                .image_url(artworkRecord.imageUrl())
                .image_url_compressed(artworkRecord.imageUrlCompressed())
                .storageType(artworkRecord.storageType())
                .type(artworkRecord.artType())
                .description(artworkRecord.description())
                .medium(artworkRecord.medium())
                .releasedDate(artworkRecord.releasedDate())
                .dimensions(artworkRecord.dimensions())
                .artist(artworkRecord.artist())
                .current_location(artworkRecord.currentLocation())
                .period_style(artworkRecord.periodStyle())
                .art_movement(artworkRecord.artMovement())
                .license_info(artworkRecord.licenseInfo())
                .source_url(artworkRecord.sourceUrl())
                .status(Status.APPROVED)
                .build();

        Artwork saved = repository.save(artwork);
        yearService.create(year);
        artistService.addArtistAndArtworkRelationship(artistId, year, saved.getId(), genreId);

        return saved;
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public void userLikeAnArtwork(String artworkId, String userId) {
        // Record a view when user likes
        repository.userViewedArtwork(artworkId, userId, LocalDateTime.now());
        if(repository.checkDislikeExists(artworkId , userId))
            repository.userUnDislikeAArtwork(artworkId,userId);
        if(!repository.checkLikeExists(artworkId , userId))
            repository.userLikeAnArtwork(artworkId,userId, LocalDateTime.now());
    }

    @Override
    public void userDislikeAnArtwork(String artworkId, String userId) {
        // Record a view when user dislikes
        repository.userViewedArtwork(artworkId, userId, LocalDateTime.now());
        if(!repository.checkLikeExists(artworkId , userId) &&
                !repository.checkDislikeExists(artworkId , userId))
        repository.userDislikeAnArtwork(artworkId , userId , LocalDateTime.now());
    }

    @Override
    public void userUnLikeAnArtwork(String artworkId, String userId) {
        repository.userUnLikeAArtwork(artworkId,userId);

    }

    @Override
    public void userUnDislikeAnArtwork(String artworkId, String userId) {
        repository.userUnDislikeAArtwork(artworkId , userId);
    }

    @Override
    public Optional<List<GetArtwork>> recommendArtwork(String userId, Integer skip, Integer limit, String country) {
        String iso2 = normalizeCountry(country);
        List<String> neighbours = GeoCountries.neighboursOf(iso2);
        Optional<List<GetArtwork>> recommendations;
        try {
            try {
                recommendations = repository.recommendArtworkSpread(userId, skip, limit, iso2, neighbours);
            } catch (RuntimeException spreadError) {
                org.slf4j.LoggerFactory.getLogger(ArtworkServiceImpl.class)
                        .error("recommendArtworkSpread failed, using tiered query: {}", spreadError.toString());
                recommendations = repository.recommendArtwork(userId, skip, limit, iso2, neighbours);
            }
        } catch (RuntimeException e) {
            // The geo-tiered personalized query must never take the feed down: retry without geo,
            // which is the pre-2.8.4 query shape that is known to work for signed-in users.
            org.slf4j.LoggerFactory.getLogger(ArtworkServiceImpl.class)
                    .error("recommendArtwork with country={} failed, retrying without geo: {}", iso2, e.toString());
            recommendations = repository.recommendArtwork(userId, skip, limit, null, java.util.Collections.emptyList());
        }
        // Guests and users without a graph node/signal get an empty personalized list;
        // serve popular artworks instead of an empty feed
        if (recommendations.isEmpty() || recommendations.get().isEmpty()) {
            return popularArtwork(userId, skip, limit, iso2);
        }
        return recommendations;
    }

    private String normalizeCountry(String country) {
        if (!GeoCountries.isIso2(country == null ? null : country.trim().toUpperCase(java.util.Locale.ROOT))) {
            return null;
        }
        return country.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public Optional<List<GetArtwork>> popularArtwork(String userId, Integer skip, Integer limit, String country) {
        String iso2 = normalizeCountry(country);
        List<String> neighbours = GeoCountries.neighboursOf(iso2);
        try {
            return repository.popularArtworkSpread(userId, skip, limit, iso2, neighbours);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(ArtworkServiceImpl.class)
                    .error("popularArtworkSpread failed, using tiered query: {}", e.toString());
            return repository.popularArtwork(userId, skip, limit, iso2, neighbours);
        }
    }

    @Override
    public Optional<List<GetArtwork>> newArrivalArtwork(String userId, Integer skip, Integer limit) {
        return repository.newArrivalArtwork(userId,skip,limit);
    }

    @Override
    public Optional<List<GetArtwork>> recommendedArtworkForToday(String userId, Integer skip, Integer limit) {
        Optional<List<GetArtwork>> recommendations = repository.recommendedArtworkForToday(userId, skip, limit);
        // Same guest fallback as recommendArtwork: never an empty feed for unknown users
        if (recommendations.isEmpty() || recommendations.get().isEmpty()) {
            return repository.popularArtwork(userId, skip, limit, null, java.util.Collections.emptyList());
        }
        return recommendations;
    }

    @Override
    public GetArtwork todayBiggestHit() {
        return repository.todayBiggestHit();
    }

    @Override
    public Optional<List<GetArtwork>> moreFromArtist(
            String artistId,
            String currentArtworkId,
           String userId
    ) {
        return repository.moreFromArtist(artistId,currentArtworkId,userId);
    }

    @Override
    public Optional<List<GetArtwork>> similarGenreArtworks(
            String currentArtworkId,
            String userId
    ) {
        return repository.similarGenreArtworks(currentArtworkId,userId);
    }



    @Override
    public Optional<GetArtwork> getArtworkById(String userId ,String artworkId) {
        return repository.findByIdProjection(userId , artworkId);
    }

    // New APIs
    @Override
    public long getArtworkLikesCount(String artworkId) {
        return repository.getArtworkLikesCount(artworkId);
    }

    @Override
    public long getArtworkCommentsCount(String artworkId) {
        return repository.getArtworkCommentsCount(artworkId);
    }

    @Override
    public java.util.List<com.basic.JWTSecurity.artwork_server.model.projection.UserProjection> getUsersWhoLikedArtwork(String artworkId, int skip, int limit) {
        return repository.getUsersWhoLikedArtwork(artworkId, skip, limit);
    }

    @Override
    public long getUsersWhoLikedArtworkCount(String artworkId) {
        return repository.getUsersWhoLikedArtworkCount(artworkId);
    }

    @Override
    public void userViewedArtwork(String artworkId, String userId) {
        repository.userViewedArtwork(artworkId, userId, LocalDateTime.now());
    }

    @Override
    public com.basic.JWTSecurity.artwork_server.dto.AnalyticsResponse getArtworkAnalytics(String artworkId, String bucket, LocalDateTime from, LocalDateTime to) {
        LocalDateTime fromTs = from != null ? from : LocalDateTime.now().minusDays(30);
        LocalDateTime toTs = to != null ? to : LocalDateTime.now();

        var views = repository.getArtworkAnalyticsBuckets(artworkId, "VIEWED", bucket, fromTs, toTs);
        var likes = repository.getArtworkAnalyticsBuckets(artworkId, "LIKES", bucket, fromTs, toTs);
        var dislikes = repository.getArtworkAnalyticsBuckets(artworkId, "DISLIKES", bucket, fromTs, toTs);

        return com.basic.JWTSecurity.artwork_server.dto.AnalyticsResponse.builder()
                .artworkId(artworkId)
                .bucket(bucket)
                .from(fromTs)
                .to(toTs)
                .views(views)
                .likes(likes)
                .dislikes(dislikes)
                .build();
    }

    @Override
    public void updateArtworkStatus(String artworkId, com.basic.JWTSecurity.artwork_server.model.Status status) {
        repository.updateArtworkStatus(artworkId, status.name());
    }
}
