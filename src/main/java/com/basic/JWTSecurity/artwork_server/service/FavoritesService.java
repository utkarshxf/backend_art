package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Favorites;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetFavorites;

import java.util.List;

public interface FavoritesService {

    Favorites create(Favorites favorites, String userId);
    void deleteById(String id);
    void addSongIntoFavorites(String favoritesId, String artworkId);
    void removeArtworkFromFavorites(String favoritesId, String artworkId);

    List<GetFavorites> getFavorites(String userId);

    List<GetArtwork> getArtworksbyID(String favoriteId);

}
