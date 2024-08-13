package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


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
                .storageId(artworkRecord.storageId())
                .storageType(artworkRecord.storageType())
                .type(artworkRecord.artType())
                .duration(artworkRecord.duration())
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
    public void userLikeAArtwork(String artworkId, String userId) {
        System.out.println("artworkId ID: " + userId.toLowerCase());

        System.out.println("Artwork ID: " + artworkId);

        repository.userLikeAArtwork(artworkId,userId, LocalDateTime.now());

    }

    @Override
    public void userDisLikeAArtwork(String artworkId, String userId) {
        repository.userDisLikeAArtwork(artworkId,userId);

    }
}
