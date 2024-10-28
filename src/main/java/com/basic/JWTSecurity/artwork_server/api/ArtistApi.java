package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.get_models.GetArtist;
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

    @GetMapping()
    public List<ArtistProjection> getAllArtist(@RequestParam String query ,@RequestParam Integer responseSize)
    {
        return artistService.getAllArtist(query,responseSize);
    }

    @GetMapping("/getArtist")
    public GetArtist getArtistByUserID(@RequestParam String artistId) {
        return artistService.getArtistByUserID(artistId);
    }
}
