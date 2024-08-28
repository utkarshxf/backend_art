package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.projection.GalleryProjection;
import com.basic.JWTSecurity.artwork_server.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class GalleryApi {

    private  final GalleryService galleryService;

    @PostMapping("/artist/{artistId}")
    public ResponseEntity<Gallery> createNewGallery(@RequestBody Gallery requestRecord, @PathVariable String artistId, @RequestParam Integer releasedYear){

        galleryService.create(requestRecord,releasedYear,artistId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @GetMapping()
    public List<GalleryProjection> getGallery(@RequestParam String GalleryName)
    {
        return galleryService.getAllGallery(GalleryName);
    }

    @PutMapping("/{galleryId}/user/{userId}/like")
    public  void userLikeAnGallery(@PathVariable String galleryId,@PathVariable String userId){
        galleryService.userLikeGallery(galleryId,userId);
    }


    @PutMapping("/{galleryId}/user/{userId}/dislike")
    public  void userDisLikeGallery(@PathVariable String galleryId, @PathVariable String userId){
        galleryService.userDikeLikeGallery(galleryId,userId);
    }


}
