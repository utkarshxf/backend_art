package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Favorites;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetFavorites;
import com.basic.JWTSecurity.artwork_server.repository.FavoritesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class FavoritesServiceImpl implements FavoritesService {

    private final FavoritesRepository favoritesRepository;
    @Override
    public Favorites create(Favorites favorites, String userId) {

        Favorites favorites1 = Favorites.builder()
                .title(favorites.getTitle())
                .description(favorites.getDescription()).build();
        Favorites saved = favoritesRepository.save(favorites1);
        favoritesRepository.addFavoritesAndUserRelationship(saved.getId(),userId, LocalDateTime.now());
        return saved;
    }

    @Override
    public void deleteById(String id) {

        favoritesRepository.deleteById(id);

    }

    @Override
    public void addSongIntoFavorites(String favoritesId, String artworkId) {
        System.out.println("favoritesId: " + favoritesId);
        System.out.println("artworkId: " + artworkId );
        favoritesRepository.addArtworkToFavorites(favoritesId, artworkId, LocalDateTime.now());

    }

    @Override
    public void removeArtworkFromFavorites(String favoritesId, String artworkId) {

        favoritesRepository.removeArtworkFromFavorites(favoritesId, artworkId);

    }

    @Override
    public List<GetFavorites> getFavorites(String userId) {
        return favoritesRepository.getFavoritesByUserId(userId);
    }
}
