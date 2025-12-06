package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtworkStatsResponse;
import com.basic.JWTSecurity.artwork_server.dto.LikedUsersPage;
import com.basic.JWTSecurity.artwork_server.dto.AnalyticsResponse;
import com.basic.JWTSecurity.artwork_server.dto.StatusUpdateRequest;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtworkProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

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

    @GetMapping("/popular")
    public ResponseEntity<?> popularArtwork(
            @RequestParam String userId,
            @RequestParam Integer skip,
            @RequestParam Integer limit) {
        Optional<List<GetArtwork>> artworks = artworkService.popularArtwork(userId, skip, limit);
        return new ResponseEntity<>(artworks, HttpStatus.OK);
    }

    @GetMapping("/new-arrivals")
    public ResponseEntity<?> newArrivalArtwork(
            @RequestParam String userId,
            @RequestParam Integer skip,
            @RequestParam Integer limit) {
        Optional<List<GetArtwork>> artworks = artworkService.newArrivalArtwork(userId, skip, limit);
        return new ResponseEntity<>(artworks, HttpStatus.OK);
    }

    @GetMapping("/recommended-today")
    public ResponseEntity<?> recommendedArtworkForToday(
            @RequestParam String userId,
            @RequestParam Integer skip,
            @RequestParam Integer limit) {
        Optional<List<GetArtwork>> artworks = artworkService.recommendedArtworkForToday(userId, skip, limit);
        return new ResponseEntity<>(artworks, HttpStatus.OK);
    }

    @GetMapping("/today-biggest-hit")
    public ResponseEntity<?> todayBiggestHit() {
        GetArtwork artwork = artworkService.todayBiggestHit();
        return new ResponseEntity<>(artwork, HttpStatus.OK);
    }

    @GetMapping("/similarGenreArtworks")
    public ResponseEntity<?> similarGenreArtworks(
            @RequestParam String currentArtworkId,
            @RequestParam String userId) {
        Optional<List<GetArtwork>> artworks = artworkService.similarGenreArtworks(currentArtworkId, userId);
        return new ResponseEntity<>(artworks, HttpStatus.OK);
    }
    @GetMapping("/moreFromArtist")
    public ResponseEntity<?> moreFromArtist(
            @RequestParam String artistId,
            @RequestParam String currentArtworkId,
            @RequestParam String userId) {
        Optional<List<GetArtwork>> artworks = artworkService.moreFromArtist(artistId, currentArtworkId, userId);
        return new ResponseEntity<>(artworks, HttpStatus.OK);
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

    // 2) Artwork stats: likes and comments
    @GetMapping("/{artworkId}/stats")
    public ResponseEntity<ArtworkStatsResponse> getArtworkStats(@PathVariable String artworkId) {
        long likes = artworkService.getArtworkLikesCount(artworkId);
        long comments = artworkService.getArtworkCommentsCount(artworkId);
        return ResponseEntity.ok(new ArtworkStatsResponse(artworkId, likes, comments));
    }

    // 3) Paginated users who liked artwork
    @GetMapping("/{artworkId}/likes/users")
    public ResponseEntity<LikedUsersPage> getUsersWhoLikedArtwork(@PathVariable String artworkId,
                                                                 @RequestParam(defaultValue = "0") Integer skip,
                                                                 @RequestParam(defaultValue = "20") Integer limit) {
        LikedUsersPage page = LikedUsersPage.builder()
                .artworkId(artworkId)
                .skip(skip)
                .limit(limit)
                .total(artworkService.getUsersWhoLikedArtworkCount(artworkId))
                .users(artworkService.getUsersWhoLikedArtwork(artworkId, skip, limit))
                .build();
        return ResponseEntity.ok(page);
    }

    // 4) Create a VIEWED relation
    @PostMapping("/{artworkId}/view/{userId}")
    public ResponseEntity<Void> userViewedArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        artworkService.userViewedArtwork(artworkId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 5) Analysis API
    @GetMapping("/{artworkId}/analysis")
    public ResponseEntity<AnalyticsResponse> getArtworkAnalysis(@PathVariable String artworkId,
                                                                @RequestParam(defaultValue = "day") String bucket,
                                                                @RequestParam(required = false) LocalDateTime from,
                                                                @RequestParam(required = false) LocalDateTime to) {
        AnalyticsResponse response = artworkService.getArtworkAnalytics(artworkId, bucket, from, to);
        return ResponseEntity.ok(response);
    }

    // 6) Update artwork status
    @PutMapping("/{artworkId}/status")
    public ResponseEntity<Void> updateArtworkStatus(@PathVariable String artworkId,
                                                    @RequestBody StatusUpdateRequest request) {
        artworkService.updateArtworkStatus(artworkId, request.getStatus());
        return ResponseEntity.ok().build();
    }

}
