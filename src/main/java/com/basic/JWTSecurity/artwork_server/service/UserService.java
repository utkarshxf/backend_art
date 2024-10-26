package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProfileProjection;
import org.springframework.http.ResponseEntity;

public interface UserService {

    User createUser(UserRegistrationRequestRecord user);
    void deleteByUserId(String id);
    void userFollowArtist(String userId,String artistId);
    void userUnFollowArtist(String userId,String artistId);

    UserProfileProjection getUserById(String userId);
}
