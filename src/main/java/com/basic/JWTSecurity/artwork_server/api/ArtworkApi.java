package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/artwork")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class ArtworkApi {

    private  final ArtworkService artworkService;

    @PostMapping("/artist/{artistId}")
    public ResponseEntity<ArtworkRecord> createNewArtwork(@RequestBody ArtworkRecord requestRecord, @PathVariable String artistId){

        artworkService.create(requestRecord,artistId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/recommend")
    public ResponseEntity<?> recommendArtwork(@RequestParam String userId, @RequestParam Integer skip, @RequestParam Integer limit)
    {
        Optional<List<GetArtwork>> artworks = artworkService.recommendArtwork(userId,skip,limit);
        return new ResponseEntity<>(artworks,HttpStatus.OK);
    }


    @GetMapping("/{userId}/{artworkId}")
    public ResponseEntity<?> artworkByArtworkId(@PathVariable String userId ,@PathVariable String artworkId){
        Optional<GetArtwork> artworks = artworkService.getArtworkById(userId , artworkId);
        return new ResponseEntity<>(artworks,HttpStatus.OK);
    }


    @PutMapping("/user/like/{artworkId}/{userId}")
    public void userLikeAnArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        System.out.println("Artist ID: " + artworkId + userId);
        artworkService.userLikeAnArtwork(artworkId, userId);
    }

    @PutMapping("/user/unlike/{artworkId}/{userId}")
    public void userUnlikeAnArtwork(@PathVariable String artworkId , @PathVariable String userId){
        artworkService.userUnLikeAnArtwork(artworkId, userId);
    }

    @PutMapping("/user/dislike/{artworkId}/{userId}")
    public void userDislikeAnArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        System.out.println("Artist ID: " + artworkId + userId);
        artworkService.userDislikeAnArtwork(artworkId, userId);
    }
}
