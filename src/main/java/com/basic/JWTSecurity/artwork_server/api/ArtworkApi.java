package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.service.ArtworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('USER')")
@RequestMapping("/artwork")
@RequiredArgsConstructor
public class ArtworkApi {

    private  final ArtworkService artworkService;

    @PostMapping("/artist/{artistId}")
    public ResponseEntity<ArtworkRecord> createNewArtwork(@RequestBody ArtworkRecord requestRecord, @PathVariable String artistId){

        artworkService.create(requestRecord,artistId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/user/like/{artworkId}/{userId}")
    public void userLikeAArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        artworkService.userLikeAArtwork(artworkId, userId);
    }

}
