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
import org.springframework.security.access.prepost.PreAuthorize;
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

    @org.springframework.beans.factory.annotation.Value("${spring.app.adminUsernames:utkarshxf}")
    private String adminUsernames;

    @PreAuthorize("#artistId == authentication.name")
    @PostMapping("/artist/{artistId}")
    public ResponseEntity<ArtworkRecord> createNewArtwork(@RequestBody ArtworkRecord requestRecord, @PathVariable String artistId){

        artworkService.create(requestRecord,artistId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/recommend")
    public ResponseEntity<?> recommendArtwork(@RequestParam String userId, @RequestParam Integer skip, @RequestParam Integer limit,
                                              @RequestParam(required = false) String country)
    {
        com.basic.JWTSecurity.artwork_server.service.ArtworkServiceImpl.LAST_GEO_ERROR.set(null);
        Optional<List<GetArtwork>> artworks = artworkService.recommendArtwork(userId,skip,limit,country);
        String geoErr = com.basic.JWTSecurity.artwork_server.service.ArtworkServiceImpl.LAST_GEO_ERROR.get();
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        ok.header("X-Geo-Mode", geoErr == null ? "geo" : "fallback");
        // DB error text only for catalogue-admin callers (never to ordinary clients).
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() && auth.getName() != null
                && java.util.Arrays.stream(adminUsernames.split(",")).map(String::trim).anyMatch(auth.getName()::equals);
        if (geoErr != null && isAdmin) ok.header("X-Geo-Error", geoErr.replaceAll("[^\\x20-\\x7E]", " "));
        return ok.body(artworks);
    }

    @GetMapping("/popular")
    public ResponseEntity<?> popularArtwork(
            @RequestParam String userId,
            @RequestParam Integer skip,
            @RequestParam Integer limit,
            @RequestParam(required = false) String country) {
        Optional<List<GetArtwork>> artworks = artworkService.popularArtwork(userId, skip, limit, country);
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


    @PreAuthorize("#userId == authentication.name")
    @PutMapping("/user/like/{artworkId}/{userId}")
    public void userLikeAnArtwork(@PathVariable String artworkId, @PathVariable String userId) {
        System.out.println("Artist ID: " + artworkId + userId);
        artworkService.userLikeAnArtwork(artworkId, userId);
    }

    @PreAuthorize("#userId == authentication.name")
    @PutMapping("/user/unlike/{artworkId}/{userId}")
    public void userUnlikeAnArtwork(@PathVariable String artworkId , @PathVariable String userId){
        artworkService.userUnLikeAnArtwork(artworkId, userId);
    }

    @PreAuthorize("#userId == authentication.name")
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
    @PreAuthorize("#userId == authentication.name")
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
    @PreAuthorize("@owner.ofArtwork(#artworkId, authentication.name)")
    @PutMapping("/{artworkId}/status")
    public ResponseEntity<Void> updateArtworkStatus(@PathVariable String artworkId,
                                                    @RequestBody StatusUpdateRequest request) {
        artworkService.updateArtworkStatus(artworkId, request.getStatus());
        return ResponseEntity.ok().build();
    }

}
