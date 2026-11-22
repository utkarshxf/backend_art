package com.basic.JWTSecurity.artwork_server.story;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Reels story read-aloud. GET /story/{artworkId}?lang=hi returns the story text in that language;
// GET /story/{artworkId}/audio?lang=hi&voice=Kore returns it read aloud (audio/wav). Both are cached in
// MongoDB after the first generation. 503 means translation/audio is unavailable (no key configured or the
// free Gemini quota is used up) and the app should fall back to on-device TTS. Requires the same JWT as
// every other endpoint.
@RestController
@RequestMapping("/story")
@RequiredArgsConstructor
public class StoryApi {

    private final StoryService storyService;

    @GetMapping("/{artworkId}")
    public ResponseEntity<Map<String, String>> getStoryText(@PathVariable String artworkId,
                                                            @RequestParam(defaultValue = "en") String lang,
                                                            @RequestParam(required = false) String region) {
        StoryService.StoryText storyText = storyService.getStoryText(artworkId, lang, region);
        return ResponseEntity.ok(Map.of(
                "artworkId", storyText.artworkId(),
                "language", storyText.language(),
                "text", storyText.text()
        ));
    }

    @GetMapping("/{artworkId}/audio")
    public ResponseEntity<byte[]> getStoryAudio(@PathVariable String artworkId,
                                                @RequestParam(defaultValue = "en") String lang,
                                                @RequestParam(required = false) String voice,
                                                @RequestParam(required = false) String region) {
        StoryAudio audio = storyService.getStoryAudio(artworkId, lang, voice, region);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, audio.getContentType())
                // Audio for one artwork + language + voice never changes; let the app cache it aggressively
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(audio.getData());
    }

    // Quota exhausted (429 from Gemini): tell the app to fall back to on-device TTS and try again later
    @ExceptionHandler(GeminiService.GeminiQuotaException.class)
    public ResponseEntity<Map<String, Object>> quotaExceeded() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Story service quota used up, try again later", "status", false));
    }

    @ExceptionHandler(GeminiService.GeminiException.class)
    public ResponseEntity<Map<String, Object>> geminiFailed(GeminiService.GeminiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Story service failed: " + e.getMessage(), "status", false));
    }
}
