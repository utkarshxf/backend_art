package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Album;
import com.basic.JWTSecurity.artwork_server.model.projection.AlbumProjection;

import java.util.List;

public interface AlbumService {

    Album create(Album album, Integer releasedYear, String artistId);
    void deleteById(String id);
    void userLikeAnAlbum(String albumId,String userId);
    void userDikeLikeAnAlbum(String albumId,String userId);
    List<AlbumProjection> getAllAlbum(String albumName);
}
