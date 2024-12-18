package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.get_models.DetailedArtwork;
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
        Optional<List<ArtworkProjection>> artworks = artworkService.recommendArtwork(userId,skip,limit);
        return new ResponseEntity<>(artworks,HttpStatus.OK);
    }


    @GetMapping("/{artworkId}")
    public ResponseEntity<?> artworkByArtworkId(@PathVariable String artworkId){
        Optional<ArtworkProjection> artworks = artworkService.getArtworkById(artworkId);
        return new ResponseEntity<>(artworks,HttpStatus.OK);
    }


    @PutMapping("/user/like/{artworkId}/{userId}")
    public void userLikeAArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        System.out.println("Artist ID: " + artworkId + userId);
        artworkService.userLikeAnArtwork(artworkId, userId);
    }

}
