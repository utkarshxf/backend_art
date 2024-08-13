package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.Album;
import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('USER')")
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentApi {

    private  final CommentService commentService;

    @PostMapping("/artwork/{artworkId}/user/{userId}")
    public ResponseEntity<Album> createNewComment(@RequestBody Comment comment, @PathVariable String artworkId, @PathVariable  String userId){

        commentService.create(comment,userId,artworkId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @DeleteMapping("/{id}")
    public void deleteById( @PathVariable String id){

        commentService.deleteById(id);
    }

}