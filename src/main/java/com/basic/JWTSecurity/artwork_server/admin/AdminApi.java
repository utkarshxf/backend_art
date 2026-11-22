package com.basic.JWTSecurity.artwork_server.admin;

import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.repository.ArtistRepository;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue-admin CRUD for artists and artworks. Restores the contract the
 * catalogue-import lane used before the 2026-11-22 redeploy: partial updates
 * preserve omitted fields, and the general artwork PUT deliberately ignores a
 * status field - status changes go through the dedicated /status route.
 */
@RestController
@RequestMapping("/admin")
public class AdminApi {

    private final ArtistRepository artistRepository;
    private final ArtworkRepository artworkRepository;
    private final AdminRepository adminRepository;

    public AdminApi(ArtistRepository artistRepository,
                    ArtworkRepository artworkRepository,
                    AdminRepository adminRepository) {
        this.artistRepository = artistRepository;
        this.artworkRepository = artworkRepository;
        this.adminRepository = adminRepository;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminArtistRequest(
            String name,
            @JsonProperty("birth_date") String birthDate,
            @JsonProperty("death_date") String deathDate,
            String nationality,
            @JsonProperty("art_movement") String artMovement,
            @JsonProperty("notable_works") String notableWorks,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("wikipedia_url") String wikipediaUrl,
            String description,
            String genreId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminArtworkRequest(
            String title,
            String imageUrl,
            String imageUrlCompressed,
            String artMovement,
            String licenseInfo,
            String sourceUrl,
            String genreId,
            String description,
            Object releaseYear,
            String medium,
            String dimensions,
            String currentLocation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminArtworkUpdateRequest(
            String title,
            String imageUrl,
            String imageUrlCompressed,
            String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminStatusRequest(String status) {
    }

    @PostMapping("/artists")
    public Map<String, String> createArtist(@RequestBody AdminArtistRequest request) {
        String id = newShortArtistId();
        Artist artist = Artist.builder()
                .id(id)
                .name(request.name())
                .birth_date(request.birthDate())
                .death_date(request.deathDate())
                .nationality(request.nationality())
                .art_movement(request.artMovement())
                .notable_works(request.notableWorks())
                .image_url(request.imageUrl())
                .wikipedia_url(request.wikipediaUrl())
                .description(request.description())
                .build();
        artistRepository.save(artist);
        if (request.genreId() != null && !request.genreId().isBlank()) {
            adminRepository.linkArtistToGenre(id, request.genreId(), LocalDateTime.now());
        }
        return Map.of("id", id);
    }

    @PostMapping("/artists/{artistId}/artworks")
    public Map<String, String> createArtwork(@PathVariable String artistId,
                                             @RequestBody AdminArtworkRequest request) {
        if (!artistRepository.existsById(artistId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "artist not found");
        }
        Artwork artwork = Artwork.builder()
                .title(request.title())
                .status(Status.APPROVED)
                .artist(artistId)
                .image_url(request.imageUrl())
                .image_url_compressed(request.imageUrlCompressed())
                .art_movement(request.artMovement())
                .license_info(request.licenseInfo())
                .source_url(request.sourceUrl())
                .description(request.description())
                .medium(request.medium())
                .dimensions(request.dimensions())
                .current_location(request.currentLocation())
                .build();
        Artwork saved = artworkRepository.save(artwork);
        LocalDateTime now = LocalDateTime.now();
        adminRepository.linkArtistToArtwork(artistId, saved.getId(), now);
        if (request.genreId() != null && !request.genreId().isBlank()) {
            adminRepository.linkArtworkToGenre(saved.getId(), request.genreId(), now);
        }
        return Map.of("id", saved.getId());
    }

    @GetMapping("/artworks/{artworkId}")
    public Map<String, Object> getArtwork(@PathVariable String artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artwork not found"));
        // Exactly the fields the catalogue lane's saved responses carry - no relationship fields.
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", artwork.getId());
        response.put("title", artwork.getTitle());
        response.put("status", artwork.getStatus());
        response.put("artist", artwork.getArtist());
        response.put("storageType", artwork.getStorageType());
        response.put("type", artwork.getType());
        response.put("releasedDate", artwork.getReleasedDate());
        response.put("medium", artwork.getMedium());
        response.put("description", artwork.getDescription());
        response.put("dimensions", artwork.getDimensions());
        response.put("current_location", artwork.getCurrent_location());
        response.put("period_style", artwork.getPeriod_style());
        response.put("art_movement", artwork.getArt_movement());
        response.put("image_url", artwork.getImage_url());
        response.put("image_url_compressed", artwork.getImage_url_compressed());
        response.put("license_info", artwork.getLicense_info());
        response.put("source_url", artwork.getSource_url());
        return response;
    }

    @PutMapping("/artworks/{artworkId}")
    public Map<String, String> updateArtwork(@PathVariable String artworkId,
                                             @RequestBody AdminArtworkUpdateRequest request) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artwork not found"));
        // Partial update: replace only the fields supplied; every omitted field is preserved.
        // A status field here is deliberately ignored - status changes use PUT /admin/artworks/{id}/status.
        if (request.title() != null) {
            artwork.setTitle(request.title());
        }
        if (request.imageUrl() != null) {
            artwork.setImage_url(request.imageUrl());
        }
        if (request.imageUrlCompressed() != null) {
            artwork.setImage_url_compressed(request.imageUrlCompressed());
        }
        artworkRepository.save(artwork);
        return Map.of();
    }

    @PutMapping("/artworks/{artworkId}/status")
    public Map<String, String> updateArtworkStatus(@PathVariable String artworkId,
                                                   @RequestBody AdminStatusRequest request) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artwork not found"));
        if (request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        final Status status;
        try {
            status = Status.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown status: " + request.status());
        }
        artwork.setStatus(status);
        artworkRepository.save(artwork);
        return Map.of();
    }

    private String newShortArtistId() {
        for (int i = 0; i < 10; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (!artistRepository.existsById(candidate)) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
