package com.basic.JWTSecurity.artwork_server.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Cached read-aloud audio of one artwork's story in one language and voice, for one version of the text
// (sourceHash). Generating speech is the expensive step, so the bytes are stored once and replayed for free.
@Document(collection = "story_audio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryAudio {

    @Id
    private String id;
    private String artworkId;
    private String language;
    private String voice;
    private String sourceHash;
    private byte[] data;
    private String contentType;
    private Instant createdAt;
}
