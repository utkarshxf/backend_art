package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.model.Album;
import com.basic.JWTSecurity.artwork_server.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('USER')")
@RequestMapping("/albums")
@RequiredArgsConstructor
public class AlbumApi {

    private  final AlbumService albumService;

    @PostMapping("/artist/{artistId}")
    public ResponseEntity<Album> createNewAlbum(@RequestBody Album requestRecord, @PathVariable String artistId, @RequestParam Integer releasedYear){

        albumService.create(requestRecord,releasedYear,artistId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{albumId}/user/{userId}/like")
    public  void userLikeAnAlbum(@PathVariable String albumId,@PathVariable String userId){
        albumService.userLikeAnAlbum(albumId,userId);
    }


    @PutMapping("/{albumId}/user/{userId}/dislike")
    public  void userDisLikeAAlbum(@PathVariable String albumId,@PathVariable String userId){
        albumService.userDikeLikeAnAlbum(albumId,userId);
    }


}
