package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.Artist;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetUser;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProfileProjection;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import com.basic.JWTSecurity.artwork_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.exceptions.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ArtistService artistService;

    @Override
    public User createUser(UserRegistrationRequestRecord requestRecord) {

        User user1 = User.builder()
                .name(requestRecord.name())
                .profilePicture(requestRecord.profilePicture())
                .dob(requestRecord.dob())
                .id(requestRecord.id())
                .gender(requestRecord.gender())
                .language(requestRecord.language())
                .countryIso2(requestRecord.countryIso2())
                .build();

        Optional<UserProjection> userProjectionOptional = userRepository.findByIdProjection(requestRecord.id());
        if (userProjectionOptional.isPresent()) {
            throw new RuntimeException(String.format("User with id %s already exists", requestRecord.id()));
        }

        User save = userRepository.save(user1);
        if (requestRecord.artist()) {
            Artist artist = artistService.createNew(Artist.builder().id(requestRecord.id()).name(requestRecord.name()).image_url(requestRecord.profilePicture()).build());
            userRepository.addArtistAndUserRelationship(save.getId(), requestRecord.id(), LocalDateTime.now());
        }
        return save;
    }

    @Override
    public User updateUser(UserRegistrationRequestRecord requestRecord) {
        // Validate that the user exists
        User existingUser = userRepository.findById(requestRecord.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("User with id %s not found", requestRecord.id())
                ));

        // Update user fields, preserving existing data if new value is null
        existingUser.setName(
                requestRecord.name() != null ? requestRecord.name() : existingUser.getName()
        );
        existingUser.setProfilePicture(
                requestRecord.profilePicture() != null ? requestRecord.profilePicture() : existingUser.getProfilePicture()
        );
        existingUser.setDob(
                requestRecord.dob() != null ? requestRecord.dob() : existingUser.getDob()
        );
        existingUser.setGender(
                requestRecord.gender() != null ? requestRecord.gender() : existingUser.getGender()
        );
        existingUser.setLanguage(
                requestRecord.language() != null ? requestRecord.language() : existingUser.getLanguage()
        );
        existingUser.setCountryIso2(
                requestRecord.countryIso2() != null ? requestRecord.countryIso2() : existingUser.getCountryIso2()
        );

        User updatedUser = userRepository.save(existingUser);

        return updatedUser;
    }

    @Override
    public void deleteByUserId(String id) {

    }

    @Override
    public void userFollowArtist(String userId, String artistId) {
        userRepository.userFollowArtist(userId, artistId, LocalDateTime.now());
    }

    @Override
    public void userUnFollowArtist(String userId, String artistId) {
        userRepository.userUnFollowArtist(userId, artistId);
    }

    @Override
    public GetUser getUserById(String userId) {
        return userRepository.getUserById(userId);
    }

    @Override
    public boolean isUserIsArtistByUserId(String userId) {
        return userRepository.isUserIsArtistByUserId(userId);
    }

}
