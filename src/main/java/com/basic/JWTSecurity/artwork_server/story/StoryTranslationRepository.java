package com.basic.JWTSecurity.artwork_server.story;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StoryTranslationRepository extends MongoRepository<StoryTranslation, String> {

    Optional<StoryTranslation> findFirstByArtworkIdAndLanguageAndSourceHash(String artworkId, String language, String sourceHash);
}
