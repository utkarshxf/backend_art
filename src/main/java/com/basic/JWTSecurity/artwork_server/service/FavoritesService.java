package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Favorites;

public interface FavoritesService {

    Favorites create(Favorites favorites, String userId);
    void deleteById(String id);
    void addSongIntoFavorites(String favoritesId, String artworkId);
    void removeArtworkFromFavorites(String favoritesId, String artworkId);

}
