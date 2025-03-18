package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.dto.ArtistRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/artist")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class ArtistApi {
    private  final ArtistService artistService;

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
    public  List<GetArtist> getArtistByArtistId(@RequestParam String userId  , @RequestParam String artistId) {
        return artistService.getArtistByArtistID(userId , artistId);
    }

    @PostMapping()
    public ResponseEntity<ArtistRegistrationRequestRecord> createNewArtist(@RequestBody ArtistRegistrationRequestRecord requestRecord){
        Artist artist = Artist.builder()
                .id(requestRecord.id())
                .name(requestRecord.name())
                .birth_date(requestRecord.birth_date())
                .death_date(requestRecord.death_date())
                .nationality(requestRecord.nationality())
                .notable_works(requestRecord.notable_works())
                .art_movement(requestRecord.art_movement())
                .education(requestRecord.education())
                .awards(requestRecord.awards())
                .image_url(requestRecord.image_url())
                .wikipedia_url(requestRecord.wikipedia_url())
                .description(requestRecord.description())
                .build();
        Artist artist1 = artistService.createNew(artist);
        return ResponseEntity.status(HttpStatus.CREATED).body(requestRecord);
    }
}
