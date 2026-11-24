package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.model.Gallery;
import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetComments;
import com.basic.JWTSecurity.artwork_server.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/comments")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class CommentApi {

    private  final CommentService commentService;
    private final com.basic.JWTSecurity.safety.SafetyService safety;

    @PreAuthorize("#userId == authentication.name")
    @PostMapping("/artwork/{artworkId}/user/{userId}")
    public ResponseEntity<Gallery> createNewComment(@RequestBody Comment comment, @PathVariable String artworkId, @PathVariable  String userId){

        commentService.create(comment,userId,artworkId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/artwork/{artworkId}")
    public ResponseEntity<List<GetComments>> getComments(@PathVariable  String artworkId, Principal principal){
        List<GetComments> comments = commentService.getArtworkComments(artworkId);
        // comments by people the caller blocked (or who blocked them), and reported comments, stay out
        Set<String> hiddenUsers = safety.hiddenUsers(principal == null ? null : principal.getName());
        Set<String> hiddenComments = safety.hiddenContent("comment", comments.stream().map(GetComments::getId).toList());
        comments = comments.stream().filter(c -> !hiddenUsers.contains(c.getUserId()) && !hiddenComments.contains(c.getId())).toList();
        return new ResponseEntity<>(comments,HttpStatus.OK);
    }


    @PreAuthorize("@owner.ofComment(#id, authentication.name)")
    @DeleteMapping("/{id}")
    public void deleteById( @PathVariable String id){

        commentService.deleteById(id);
    }

}
