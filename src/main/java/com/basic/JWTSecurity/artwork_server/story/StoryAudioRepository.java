package com.basic.JWTSecurity.artwork_server.story;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StoryAudioRepository extends MongoRepository<StoryAudio, String> {

    Optional<StoryAudio> findFirstByArtworkIdAndLanguageAndVoiceAndSourceHash(String artworkId, String language, String voice, String sourceHash);
}
