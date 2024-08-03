package com.basic.JWTSecurity.repository;

import com.basic.JWTSecurity.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUsername(String username);
}