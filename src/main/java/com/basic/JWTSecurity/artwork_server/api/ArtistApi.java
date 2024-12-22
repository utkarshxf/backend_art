package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtistService;
import lombok.RequiredArgsConstructor;
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
    public  List<GetArtist> getArtistByArtistId(@RequestParam String artistId) {
        return artistService.getArtistByArtistID(artistId);
    }
}
