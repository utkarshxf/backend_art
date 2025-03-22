package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Genre;
import com.basic.JWTSecurity.artwork_server.model.projection.GenreProjection;
import com.basic.JWTSecurity.artwork_server.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService{

    private final GenreRepository genreRepository;
    @Override
    public Genre create(Genre genre) {
        return genreRepository.save(Genre.builder().name(genre.getName()).key(genre.getKey()).id(genre.getId()).build());
    }


    @Override
    public void addArtistToGenre(String genreId, String artistId) {

        genreRepository.addArtistToGenre(genreId,artistId, LocalDateTime.now());

    }

    @Override
    public void removeArtistFromGenre(String genreId, String artistId) {

        genreRepository.removeArtistFromGenre(genreId,artistId);
    }

    @Override
    public void addArtworkToGenre(String genreId, String artworkId) {

        genreRepository.addArtworkToGenre(genreId, artworkId,LocalDateTime.now());
    }

    @Override
    public void removeArtworkFromGenre(String genreId, String artworkId) {
        genreRepository.removeArtworkFromGenre(genreId, artworkId);
    }

    @Override
    public List<GenreProjection> getGenres(String genreName) {
        return genreRepository.getGenres(genreName);
    }

    @Override
    public List<GenreProjection> getAllGenres() {
        return genreRepository.getAllGenres();
    }
}
