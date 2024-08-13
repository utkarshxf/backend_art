package com.basic.JWTSecurity.auth.repository;

import com.basic.JWTSecurity.auth.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUsername(String username);
}