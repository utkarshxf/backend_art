package com.basic.JWTSecurity.auth.repository;

import com.basic.JWTSecurity.auth.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUsername(String username);

    Optional<Profile> findByPhone(String phone);

    @Query("{ $or: [ { 'username': { $regex: ?0, $options: 'i' } }, { 'id': { $regex: ?0, $options: 'i' } } ] }")
    List<Profile> searchByUsernameOrName(String keyword);

}