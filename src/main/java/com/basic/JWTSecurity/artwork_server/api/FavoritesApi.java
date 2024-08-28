package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.Favorites;
import com.basic.JWTSecurity.artwork_server.service.FavoritesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoritesApi {

    private  final FavoritesService favoritesService;

    @PostMapping("/user/{userId}")
    public ResponseEntity<Gallery> createNewAFavorites(@RequestBody Favorites requestRecord, @PathVariable String userId){
        favoritesService.create(requestRecord,userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}/artwork/{artworkId}/add-artwork")
    public  void addArtworkIntoFavorites(@PathVariable String id, @PathVariable String artworkId){
        favoritesService.addSongIntoFavorites(id,artworkId);
    }
    @PutMapping("/{id}/artwork/{artworkId}/remove-artwork")
    public  void removeArtworkFromFavorites(@PathVariable String id, @PathVariable String artworkId){
        favoritesService.removeArtworkFromFavorites(id,artworkId);
    }



}
