package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.RecommendedArtwork;
import com.basic.JWTSecurity.artwork_server.model.projection.ArtistProjection;
import com.basic.JWTSecurity.artwork_server.service.ArtistService;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
}
