package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetComments;
import com.basic.JWTSecurity.artwork_server.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/comments")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class CommentApi {

    private  final CommentService commentService;

    @PostMapping("/artwork/{artworkId}/user/{userId}")
    public ResponseEntity<Gallery> createNewComment(@RequestBody Comment comment, @PathVariable String artworkId, @PathVariable  String userId){

        commentService.create(comment,userId,artworkId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/artwork/{artworkId}")
    public ResponseEntity<List<GetComments>> getComments(@PathVariable  String artworkId){
        List<GetComments> comments = commentService.getArtworkComments(artworkId);
        return new ResponseEntity<>(comments,HttpStatus.OK);
    }


    @DeleteMapping("/{id}")
    public void deleteById( @PathVariable String id){

        commentService.deleteById(id);
    }

}
