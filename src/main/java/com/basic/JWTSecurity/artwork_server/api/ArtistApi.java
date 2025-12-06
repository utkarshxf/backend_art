package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.dto.ArtistRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtistStatsResponse;
import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtistService;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/artist")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class ArtistApi {
    private  final ArtistService artistService;
    private final UserService userService;

    @GetMapping("/search")
    public List<ArtistProjection> getAllArtist(@RequestParam String query ,@RequestParam Integer responseSize)
    {
        return artistService.getAllArtist(query,responseSize);
    }

    @GetMapping("/getArtworkByArtistId")
    public  List<GetArtwork> getArtworkByUserID(@RequestParam String userId , @RequestParam String artistId) {
        return artistService.getArtworkByUserID(userId,artistId);
    }

    @GetMapping("/getArtistByArtistId")
    public GetArtist getArtistByArtistId(@RequestParam String userId  , @RequestParam String artistId) {
        return artistService.getArtistByArtistID(userId , artistId);
    }

    @GetMapping("/getArtistByArtworkId")
    public GetArtist getArtistByArtworkId(@RequestParam String userId  , @RequestParam String artworkId) {
        return artistService.getArtistByArtworkID(userId , artworkId);
    }


    @PostMapping()
    public ResponseEntity<ArtistRegistrationRequestRecord> createNewArtist(
            @RequestParam(required = false) String userId,
            @RequestBody ArtistRegistrationRequestRecord requestRecord) {
        String country = "in";
        String profileImage = "https://ui-avatars.com/api/?name=" + requestRecord.name();
        String name = requestRecord.name();
        String userId1 = requestRecord.id();
        if(userId != null && requestRecord.id() != null){
            if(!userId.equals(requestRecord.id())){
                throw new IllegalArgumentException("User ID in request parameter and request body do not match");
            }
        }
        if(userId1 != null){
            userId = userId1;
        }

        if(userId == null){
            throw new IllegalArgumentException("Artist ID cannot be null or blank");
        }

        String fallbackCountry = requestRecord.nationality() != null
                ? requestRecord.nationality().toLowerCase()
                : "in";
        try {
            GetUser user = userService.getUserById(userId);
            if (user != null) {
                country = user.getCountryIso2().toLowerCase();
                profileImage = user.getProfilePicture();
            } else{
                country = fallbackCountry;
            }
        } catch (Exception ignored) {
            country = fallbackCountry;
            userService.createUser(
                    new UserRegistrationRequestRecord(
                            false,
                            requestRecord.id(),
                            requestRecord.name(),
                            profileImage,
                            LocalDate.of(2000, 1, 1),
                            "unspecified",
                            "en",
                            country
                    )
            );
        }
        if(requestRecord.image_url() != null){
            profileImage = requestRecord.image_url();
        }
        if(requestRecord.name() != null ){
            name = userId ;
        }

        Artist artist = Artist.builder()
                .id(userId)
                .name(name)
                .birth_date(requestRecord.birth_date())
                .death_date(requestRecord.death_date())
                .nationality(country)
                .notable_works(requestRecord.notable_works())
                .art_movement(requestRecord.art_movement())
                .education(requestRecord.education())
                .awards(requestRecord.awards())
                .image_url(profileImage)
                .wikipedia_url(requestRecord.wikipedia_url())
                .description(requestRecord.description())
                .build();

        Artist artist1 = artistService.createNew(artist);

        return ResponseEntity.status(HttpStatus.CREATED).body(requestRecord);
    }


    @PutMapping("/{artistId}")
    public ResponseEntity<?> updateArtist(
            @PathVariable String artistId,
            @RequestBody ArtistRegistrationRequestRecord requestRecord
    ) {
        if(!Objects.equals(artistId, requestRecord.id()))
        {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "user Id does not match");
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
        }
        Artist updatedArtist = artistService.updateArtist(requestRecord);

        return ResponseEntity.ok(requestRecord);
    }

    @GetMapping("/getArtistStats")
    public ResponseEntity<ArtistStatsResponse> getArtistByArtistId(@RequestParam String artistId) {
        return ResponseEntity.status(HttpStatus.OK).body(artistService.getArtistStats(artistId));
    }

}
