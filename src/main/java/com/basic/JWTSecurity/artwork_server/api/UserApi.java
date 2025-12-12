package com.basic.JWTSecurity.artwork_server.api;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final UserService userService;
    private final ProfileService profileService;

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

    @GetMapping("/isValidUsername")
    public ResponseEntity<?> isValidUsername(@RequestParam String username) {
        try {
            Map<String, Object> response = profileService.validateUsername(username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", false);
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/searchUsers")
    public ResponseEntity<?> searchUsers(
            @RequestParam String key,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        try {
            List<Map<String, Object>> users = userService.searchUsersByKeyword(key, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("users", users);
            response.put("count", users.size());
            response.put("status", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", false);
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }
}
