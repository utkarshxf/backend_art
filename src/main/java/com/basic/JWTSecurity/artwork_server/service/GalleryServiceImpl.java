package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.projection.GalleryProjection;
import com.basic.JWTSecurity.artwork_server.repository.GalleryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.basic.JWTSecurity.artwork_server.model.Status.DRAFT;


@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryServiceImpl implements GalleryService {

    private final GalleryRepository galleryRepository;
    private final YearService yearService;
    @Override
    public Gallery create(GalleryProjection gallery, Integer releasedYear, String artistId) {

        Gallery build = Gallery.builder()
                .name(gallery.getName())
                .description(gallery.getDescription())
                .coverUrl(gallery.getCoverUrl())
                .status(gallery.getStatus()).build();
        Gallery save = galleryRepository.save(build);
        yearService.create(releasedYear);
        galleryRepository.addReleasedYearAndArtist(artistId,save.getId(),releasedYear,LocalDateTime.now());
        return save;
    }

    @Override
    public void deleteById(String id) {

        galleryRepository.deleteById(id);
    }

    @Override
    public void userLikeGallery(String galleryId, String userId) {


        galleryRepository.userLikesGallery(userId, galleryId, LocalDateTime.now());

    }

    @Override
    public void userDikeLikeGallery(String galleryId, String userId) {

        galleryRepository.userDislikeGallery(userId, galleryId);
    }

    @Override
    public List<GalleryProjection> getAllGallery(String galleryName) {
        return galleryRepository.getGallery(galleryName);
    }
}
