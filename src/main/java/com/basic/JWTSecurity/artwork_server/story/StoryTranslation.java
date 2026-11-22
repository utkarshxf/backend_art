package com.basic.JWTSecurity.artwork_server.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Cached translation of one artwork's story (description) into one language, for one version of the source
// text (sourceHash). Artwork stories are public gallery text, so one cached entry serves every user.
@Document(collection = "story_translation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryTranslation {

    @Id
    private String id;
    private String artworkId;
    private String language;
    private String sourceHash;
    private String text;
    private Instant createdAt;
}
