package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


        Artwork artwork = Artwork.builder()
                .name(artworkRecord.title())
                .imageUrl(artworkRecord.imageUrl())
                .storageType(artworkRecord.storageType())
                .type(artworkRecord.artType())
                .description(artworkRecord.description())
                .madeWith(artworkRecord.madeWith())
                .releasedDate(artworkRecord.releasedDate())
                .status(Status.DRAFT)
                .build();

        Artwork saved = repository.save(artwork);
        yearService.create(artworkRecord.releaseYear());
        artistService.addArtistAndArtworkRelationship(artistId, artworkRecord.releaseYear(), saved.getId(), artworkRecord.genreId());

        return saved;
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public void userLikeAnArtwork(String artworkId, String userId) {
        if(repository.checkDislikeExists(artworkId , userId))
            repository.userUnDislikeAArtwork(artworkId,userId);
        if(!repository.checkLikeExists(artworkId , userId))
            repository.userLikeAnArtwork(artworkId,userId, LocalDateTime.now());
    }

    @Override
    public void userDislikeAnArtwork(String artworkId, String userId) {
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
    public Optional<List<GetArtwork>> recommendArtwork(String userId, Integer skip, Integer limit) {
        return repository.recommendArtwork(userId,skip,limit);
    }

    @Override
    public Optional<List<GetArtwork>> popularArtwork(String userId, Integer skip, Integer limit) {
        return repository.popularArtwork(userId,skip,limit);
    }

    @Override
    public Optional<List<GetArtwork>> newArrivalArtwork(String userId, Integer skip, Integer limit) {
        return repository.newArrivalArtwork(userId,skip,limit);
    }

    @Override
    public Optional<List<GetArtwork>> recommendedArtworkForToday(String userId, Integer skip, Integer limit) {
        return repository.recommendedArtworkForToday(userId,skip,limit);
    }

    @Override
    public GetArtwork todayBiggestHit() {
        return repository.todayBiggestHit();
    }

    @Override
    public Optional<GetArtwork> getArtworkById(String userId ,String artworkId) {
        return repository.findByIdProjection(userId , artworkId);
    }
}
