package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.dto.AdminArtistUpsertRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtistRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.repository.ArtistRepository;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import com.basic.JWTSecurity.artwork_server.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ArtistRepository artistRepository;
    private final ArtworkRepository artworkRepository;
    private final GenreRepository genreRepository;
    private final ArtistService artistService;
    private final ArtworkService artworkService;
    private final YearService yearService;

    // Same id convention as the catalogue importer (md5(text)[:8]) so admin-created records sit naturally
    // alongside the seeded ones
    private static String idFromName(String name) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private Genre requireGenre(String genreId) {
        return genreRepository.findById(genreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Genre does not exist: " + genreId));
    }

    @Override
    public String createArtist(AdminArtistUpsertRecord record) {
        if (record.name() == null || record.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist name is required");
        }
        String id = (record.id() == null || record.id().isBlank()) ? idFromName(record.name().trim()) : record.id();
        if (artistService.findById(id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artist already exists: " + id);
        }
        if (record.genreId() != null && !record.genreId().isBlank()) {
            requireGenre(record.genreId());
        }

        Artist artist = Artist.builder()
                .id(id)
                .name(record.name())
                .birth_date(record.birth_date())
                .death_date(record.death_date())
                .nationality(record.nationality())
                .notable_works(record.notable_works())
                .art_movement(record.art_movement())
                .education(record.education())
                .awards(record.awards())
                .image_url(record.image_url())
                .wikipedia_url(record.wikipedia_url())
                .description(record.description())
                .build();

        // ArtistServiceImpl.createNew only links a User when one already owns this id (MATCH, no MERGE), so a
        // catalogue artist never gets a fabricated persona here
        artistService.createNew(artist);

        if (record.genreId() != null && !record.genreId().isBlank()) {
            genreRepository.addArtistToGenre(record.genreId(), id, LocalDateTime.now());
        }
        return id;
    }

    @Override
    public void updateArtist(String artistId, AdminArtistUpsertRecord record) {
        if (artistService.findById(artistId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found: " + artistId);
        }
        // Null-merge update semantics live in ArtistServiceImpl.updateArtist; its User-profile sync is a no-op
        // for catalogue artists because no User is linked to them
        artistService.updateArtist(new ArtistRegistrationRequestRecord(
                artistId,
                record.name(),
                record.birth_date(),
                record.death_date(),
                record.nationality(),
                record.notable_works(),
                record.art_movement(),
                record.education(),
                record.awards(),
                record.image_url(),
                record.wikipedia_url(),
                record.description()
        ));
        if (record.genreId() != null && !record.genreId().isBlank()) {
            requireGenre(record.genreId());
            genreRepository.addArtistToGenre(record.genreId(), artistId, LocalDateTime.now());
        }
    }

    @Override
    public String createArtwork(String artistId, ArtworkRecord record) {
        if (artistService.findById(artistId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found: " + artistId);
        }
        if (record.title() == null || record.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artwork title is required");
        }
        if (record.genreId() == null || record.genreId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "genreId is required");
        }
        requireGenre(record.genreId());

        Artwork artwork = Artwork.builder()
                .title(record.title())
                .image_url(record.imageUrl())
                .image_url_compressed(record.imageUrlCompressed())
                .storageType(record.storageType())
                .type(record.artType())
                .description(record.description())
                .medium(record.medium())
                // Verbatim only: a known exact date from the source, never a fabricated "year-01-01" for circa works
                .releasedDate(record.releasedDate())
                .dimensions(record.dimensions())
                .artist(record.artist() != null ? record.artist() : artistId)
                .current_location(record.currentLocation())
                .period_style(record.periodStyle())
                .art_movement(record.artMovement())
                .license_info(record.licenseInfo())
                .source_url(record.sourceUrl())
                .status(Status.APPROVED)
                .build();

        Artwork saved = artworkRepository.save(artwork);
        String artworkId = saved.getId();

        artistRepository.linkArtistToArtwork(artistId, artworkId, LocalDateTime.now());
        genreRepository.addArtworkToGenre(record.genreId(), artworkId, LocalDateTime.now());

        // Unknown or circa year: no Year node, no RELEASED_IN edge - the record honestly has no year.
        // Feed queries only OPTIONAL MATCH the year, and the Android client renders a missing year as nothing.
        if (record.releaseYear() != null) {
            yearService.create(record.releaseYear());
            artworkRepository.linkReleasedIn(artworkId, record.releaseYear(), LocalDateTime.now());
        }
        return artworkId;
    }

    @Override
    public void updateArtwork(String artworkId, ArtworkRecord record) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artwork not found: " + artworkId));

        if (record.title() != null) artwork.setTitle(record.title());
        if (record.imageUrl() != null) artwork.setImage_url(record.imageUrl());
        if (record.imageUrlCompressed() != null) artwork.setImage_url_compressed(record.imageUrlCompressed());
        if (record.storageType() != null) artwork.setStorageType(record.storageType());
        if (record.artType() != null) artwork.setType(record.artType());
        if (record.description() != null) artwork.setDescription(record.description());
        if (record.medium() != null) artwork.setMedium(record.medium());
        if (record.releasedDate() != null) artwork.setReleasedDate(record.releasedDate());
        if (record.dimensions() != null) artwork.setDimensions(record.dimensions());
        if (record.artist() != null) artwork.setArtist(record.artist());
        if (record.currentLocation() != null) artwork.setCurrent_location(record.currentLocation());
        if (record.periodStyle() != null) artwork.setPeriod_style(record.periodStyle());
        if (record.artMovement() != null) artwork.setArt_movement(record.artMovement());
        if (record.licenseInfo() != null) artwork.setLicense_info(record.licenseInfo());
        if (record.sourceUrl() != null) artwork.setSource_url(record.sourceUrl());

        artworkRepository.save(artwork);

        if (record.genreId() != null && !record.genreId().isBlank()) {
            requireGenre(record.genreId());
            artworkRepository.clearGenres(artworkId);
            genreRepository.addArtworkToGenre(record.genreId(), artworkId, LocalDateTime.now());
        }
        if (record.releaseYear() != null) {
            yearService.create(record.releaseYear());
            artworkRepository.clearReleasedIn(artworkId);
            artworkRepository.linkReleasedIn(artworkId, record.releaseYear(), LocalDateTime.now());
        }
    }

    @Override
    public void updateArtworkStatus(String artworkId, Status status) {
        artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artwork not found: " + artworkId));
        artworkService.updateArtworkStatus(artworkId, status);
    }

    @Override
    public Map<String, Object> getArtworkForAudit(String artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artwork not found: " + artworkId));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", artwork.getId());
        map.put("title", artwork.getTitle());
        map.put("status", artwork.getStatus() != null ? artwork.getStatus().name() : null);
        map.put("artist", artwork.getArtist());
        map.put("storageType", artwork.getStorageType() != null ? artwork.getStorageType().name() : null);
        map.put("type", artwork.getType() != null ? artwork.getType().name() : null);
        map.put("releasedDate", artwork.getReleasedDate() != null ? artwork.getReleasedDate().toString() : null);
        map.put("medium", artwork.getMedium());
        map.put("description", artwork.getDescription());
        map.put("dimensions", artwork.getDimensions());
        map.put("current_location", artwork.getCurrent_location());
        map.put("period_style", artwork.getPeriod_style());
        map.put("art_movement", artwork.getArt_movement());
        map.put("image_url", artwork.getImage_url());
        map.put("image_url_compressed", artwork.getImage_url_compressed());
        map.put("license_info", artwork.getLicense_info());
        map.put("source_url", artwork.getSource_url());
        return map;
    }

    @Override
    public Map<String, String> createGenre(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Genre name is required");
        }
        String trimmed = name.trim();
        String id = idFromName(trimmed);
        Genre genre = genreRepository.findById(id).orElse(null);
        if (genre == null) {
            genre = genreRepository.save(Genre.builder().id(id).key(id).name(trimmed).build());
        }
        return Map.of("id", genre.getId(), "key", genre.getKey() != null ? genre.getKey() : id, "name", genre.getName());
    }
}
