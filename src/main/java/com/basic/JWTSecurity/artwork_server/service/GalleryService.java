package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.projection.GalleryProjection;

import java.util.List;

public interface GalleryService {

    Gallery create(Gallery gallery, Integer releasedYear, String artistId);
    void deleteById(String id);
    void userLikeGallery(String galleryId, String userId);
    void userDikeLikeGallery(String galleryId, String userId);
    List<GalleryProjection> getAllGallery(String galleryName);
}
