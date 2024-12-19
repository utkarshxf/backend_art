package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProfileProjection;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
//@PreAuthorize("hasRole('USER')")
@RequestMapping("/users")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class UserApi {

    private  final UserService userService;

    @PostMapping
    public ResponseEntity<UserRegistrationRequestRecord> createNewUser(@RequestBody UserRegistrationRequestRecord requestRecord){
        User user = userService.createUser(requestRecord);
        return ResponseEntity.status(HttpStatus.CREATED).body(requestRecord);
    }


    @PutMapping("/{userId}/artist/{artistId}/follow")
    public  void followArtist(@PathVariable String userId,@PathVariable String artistId){
        userService.userFollowArtist(userId,artistId);
    }

    @PutMapping("/{userId}/artist/{artistId}/unfollow")
    public  void unfollowArtist(@PathVariable String userId,@PathVariable String artistId){
        userService.userUnFollowArtist(userId,artistId);
    }

    @GetMapping("/getUserByUserId/{userId}")
    GetUser getUserByUserId(@PathVariable String userId  , @PathVariable String currentUserId){
        return userService.getUserById(userId , currentUserId);
    }

}
