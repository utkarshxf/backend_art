package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.ArtistRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtistStatsResponse;
import com.basic.JWTSecurity.artwork_server.dto.TopArtistLeaderboardResponse;
import com.basic.JWTSecurity.artwork_server.dto.TopArtistsLeaderboardPage;
import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.*;
import com.basic.JWTSecurity.artwork_server.repository.ArtistRepository;
import com.basic.JWTSecurity.artwork_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.exceptions.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistServiceImpl implements  ArtistService{

    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    @Override
    public Artist createNew(Artist artist) {

        Artist artist1 = findById(artist.getId());
        if(nonNull(artist1)){
            throw  new RuntimeException(String.format("Artist with id %s already exists",artist.getId())); // todo add custom exception

        }
        Artist artist2 = Artist.builder()
                .id(artist.getId())
                .name(artist.getName())
                .birth_date(artist.getBirth_date())
                .death_date(artist.getDeath_date())
                .nationality(artist.getNationality())
                .notable_works(artist.getNotable_works())
                .art_movement(artist.getArt_movement())
                .education(artist.getEducation())
                .awards(artist.getAwards())
                .image_url(artist.getImage_url())
                .wikipedia_url(artist.getWikipedia_url())
                .description(artist.getDescription())
                .build();

        Artist newArtist =  artistRepository.save(artist2);
        if(newArtist.getId()!=null) {
            userRepository.addArtistAndUserRelationship(newArtist.getId(), newArtist.getId(), LocalDateTime.now());
            updateUserProfileFromArtist(newArtist.getId(), artist);
        }
        return newArtist;
    }

    @Override
    public List<ArtistProjection> getAllArtist(String artistName, Integer responseSize) {
        List<ArtistProjection> artists =  artistRepository.getArtist(artistName , responseSize);
        return artists;
    }

    @Override
    public Artist findById(String id) {

        ArtistProjection artistProjection = artistRepository.findByIdProjection(id).orElse(null);
        if(isNull(artistProjection)){
            return null;
        }
        return Artist.builder().id(artistProjection.getId()).name(artistProjection.getName()).image_url(artistProjection.getImageUrl()).build();
    }

    @Override
    public void deleteById(String id) {

    }

    @Override
    public void addArtistAndArtworkRelationship(String artistId, Integer releaseYear, String artworkId, String genreId) {
        System.out.println("Creating relationships between artist, artwork, year, and genre");
        System.out.println("Artist ID: " + artistId);
        System.out.println("Release Year: " + releaseYear);
        System.out.println("Artwork ID: " + artworkId);
        System.out.println("Genre ID: " + genreId);
        artistRepository.addArtistAndArtworkRelationship(artistId, releaseYear,artworkId, LocalDateTime.now(),genreId);
    }

    @Override
    public List<GetArtwork> getArtworkByUserID(String userId , String artistId) {
        List<GetArtwork> result = artistRepository.getArtworkByArtistId(userId , artistId);
        return result;
    }

    @Override
    public GetArtist getArtistByArtistID(String currentUserId , String artistId) {
        return artistRepository.getArtistById(currentUserId , artistId);
    }

    @Override
    public GetArtist getArtistByArtworkID(String currentUserId , String artworkId) {
        return artistRepository.getArtistByArtworkId(currentUserId , artworkId);
    }

    @Override
    public Artist updateArtist(ArtistRegistrationRequestRecord requestRecord) {
        // Validate that the artist exists
        Artist existingArtist = artistRepository.findById(requestRecord.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Artist with id %s not found", requestRecord.id())
                ));

        // Update artist fields, preserving existing data if new value is null
        existingArtist.setName(
                requestRecord.name() != null ? requestRecord.name() : existingArtist.getName()
        );
        existingArtist.setBirth_date(
                requestRecord.birth_date() != null ? requestRecord.birth_date() : existingArtist.getBirth_date()
        );
        existingArtist.setDeath_date(
                requestRecord.death_date() != null ? requestRecord.death_date() : existingArtist.getDeath_date()
        );
        existingArtist.setNationality(
                requestRecord.nationality() != null ? requestRecord.nationality() : existingArtist.getNationality()
        );
        existingArtist.setNotable_works(
                requestRecord.notable_works() != null ? requestRecord.notable_works() : existingArtist.getNotable_works()
        );
        existingArtist.setArt_movement(
                requestRecord.art_movement() != null ? requestRecord.art_movement() : existingArtist.getArt_movement()
        );
        existingArtist.setEducation(
                requestRecord.education() != null ? requestRecord.education() : existingArtist.getEducation()
        );
        existingArtist.setAwards(
                requestRecord.awards() != null ? requestRecord.awards() : existingArtist.getAwards()
        );
        existingArtist.setImage_url(
                requestRecord.image_url() != null ? requestRecord.image_url() : existingArtist.getImage_url()
        );
        existingArtist.setWikipedia_url(
                requestRecord.wikipedia_url() != null ? requestRecord.wikipedia_url() : existingArtist.getWikipedia_url()
        );
        existingArtist.setDescription(
                requestRecord.description() != null ? requestRecord.description() : existingArtist.getDescription()
        );

        Artist updatedArtist = artistRepository.save(existingArtist);

        // Update associated user profile if user exists
        updateUserProfileFromRequestRecord(requestRecord.id(), requestRecord);

        return updatedArtist;
    }


    @Override
    public ArtistStatsResponse getArtistStats(String artistId) {
        long followers = artistRepository.getFollowersCount(artistId);
        long artworks = artistRepository.getArtworksCount(artistId);
        long totalLikes = artistRepository.getTotalLikesAcrossArtworks(artistId);

        return ArtistStatsResponse.builder()
                .artistId(artistId)
                .followers(followers)
                .totalArtworks(artworks)
                .totalLikesOnArtworks(totalLikes)
                .build();
    }

    /**
     * Update user profile from artist data
     * Checks if user exists for the given artist ID and updates name, profile picture, and DOB
     */
    private void updateUserProfileFromArtist(String artistId, Artist artist) {
        try {
            var userOpt = userRepository.findUserByArtistId(artistId);
            if (userOpt.isPresent()) {
                var user = userOpt.get();

                // Update user fields from artist if they are not null
                if (artist.getName() != null) {
                    user.setName(artist.getName());
                }
                if (artist.getImage_url() != null) {
                    user.setProfilePicture(artist.getImage_url());
                }
                if (artist.getBirth_date() != null) {
                    try {
                        user.setDob(java.time.LocalDate.parse(artist.getBirth_date()));
                    } catch (Exception e) {
                        log.warn("Failed to parse birth_date: {}", artist.getBirth_date(), e);
                    }
                }

                userRepository.save(user);
                log.info("Updated user profile for artist ID: {}", artistId);
            }
        } catch (Exception e) {
            log.error("Error updating user profile for artist ID: {}", artistId, e);
        }
    }

    /**
     * Update user profile from artist request record
     * Checks if user exists for the given artist ID and updates name, profile picture, and DOB
     */
    private void updateUserProfileFromRequestRecord(String artistId, ArtistRegistrationRequestRecord requestRecord) {
        try {
            var userOpt = userRepository.findUserByArtistId(artistId);
            if (userOpt.isPresent()) {
                var user = userOpt.get();

                // Update user fields from request record if they are not null
                if (requestRecord.name() != null) {
                    user.setName(requestRecord.name());
                }
                if (requestRecord.image_url() != null) {
                    user.setProfilePicture(requestRecord.image_url());
                }
                if (requestRecord.birth_date() != null) {
                    try {
                        user.setDob(java.time.LocalDate.parse(requestRecord.birth_date()));
                    } catch (Exception e) {
                        log.warn("Failed to parse birth_date: {}", requestRecord.birth_date(), e);
                    }
                }

                userRepository.save(user);
                log.info("Updated user profile for artist ID: {}", artistId);
            }
        } catch (Exception e) {
            log.error("Error updating user profile for artist ID: {}", artistId, e);
        }
    }

    @Override
    public TopArtistsLeaderboardPage getTopArtistsByLikes(Integer page, Integer size) {
        // Validate and set default values for pagination
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }

        // Calculate skip value for pagination
        int skip = page * size;

        // Get top artists from repository
        List<TopArtistProjection> topArtists = artistRepository.getTopArtistsByLikes(skip, size);

        // Get total count of artists with likes
        Long totalArtists = artistRepository.getTopArtistsCount();

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalArtists / size);

        // Build response with ranking
        AtomicInteger rankCounter = new AtomicInteger(skip + 1);
        List<TopArtistLeaderboardResponse> artistResponses = topArtists.stream()
                .map(artist -> TopArtistLeaderboardResponse.builder()
                        .artistId(artist.getArtistId())
                        .name(artist.getName())
                        .imageUrl(artist.getImageUrl())
                        .totalLikes(artist.getTotalLikes())
                        .rank(rankCounter.getAndIncrement())
                        .build())
                .collect(Collectors.toList());

        return TopArtistsLeaderboardPage.builder()
                .artists(artistResponses)
                .currentPage(page)
                .totalPages(totalPages)
                .totalArtists(totalArtists)
                .pageSize(size)
                .build();
    }

}


