package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistServiceImpl implements  ArtistService{

    private final ArtistRepository artistRepository;
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
        return artistRepository.save(artist2);
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
}
