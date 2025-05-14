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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @GetMapping("/getFollowers/{artistId}")
    public ResponseEntity<List<GetUser>> getFollowers(@PathVariable String artistId){
        return null;
    }

    @GetMapping("/getFollowing/{artistId}")
    public ResponseEntity<List<GetUser>> getFollowing(@PathVariable String artistId){
        return null;
    }


    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable String userId,
            @RequestBody UserRegistrationRequestRecord requestRecord
    ) {
        if(!Objects.equals(userId, requestRecord.id()))
        {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "user Id does not match");
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
        }
        User updatedUser = userService.updateUser(requestRecord);

        return ResponseEntity.ok(requestRecord);
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
    GetUser getUserByUserId(@PathVariable String userId){
        return userService.getUserById(userId);
    }

    @GetMapping("/isUserIsArtistByUserId/{userId}")
    ResponseEntity<?> isUserIsArtistByUserId(@PathVariable String userId){
        boolean result = userService.isUserIsArtistByUserId(userId);
        return ResponseEntity.ok(result);
    }
}
