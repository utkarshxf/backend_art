package com.basic.JWTSecurity.artwork_server.story;

import com.basic.JWTSecurity.artwork_server.model.Artwork;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

// Reels story read-aloud: serves the artwork story (description) translated into the requested language and
// read aloud as audio. Both are generated once per artwork + language (+ voice) and cached in MongoDB, so the
// free Gemini quota is only spent the first time anyone asks. The source hash in the key means an edited
// story simply generates fresh entries; stale ones age out with the old text.
@Service
@RequiredArgsConstructor
public class StoryService {

    // Gemini TTS accepts long input, but a story should stay listenable and cheap; longer texts are rejected
    // so the app can fall back to on-device TTS instead of burning quota on an outlier
    private static final int MAX_TTS_CHARS = 4000;

    // Language code (app setting) -> language name (prompt). Unknown codes are passed through as-is.
    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "en", "English",
            "hi", "Hindi"
    );

    private final ArtworkRepository artworkRepository;
    private final StoryTranslationRepository translationRepository;
    private final StoryAudioRepository audioRepository;
    private final GeminiService geminiService;

    @Value("${gemini.default-voice:Kore}")
    private String defaultVoice;

    // Regions Gemini free-tier terms exclude from being served by an API client (EEA + UK + CH).
    // A missing/unknown region is treated as restricted: no Gemini call, the app uses on-device TTS.
    private static final java.util.Set<String> GEMINI_RESTRICTED_REGIONS = java.util.Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE",
            "IT", "LV", "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "GB", "CH"
    );

    public StoryText getStoryText(String artworkId, String language) {
        return getStoryText(artworkId, language, null);
    }

    public StoryText getStoryText(String artworkId, String language, String region) {
        return getStoryText(artworkId, language, region, false);
    }

    // guest=true: serve only what is already cached; guests never trigger Gemini generation.
    public StoryText getStoryText(String artworkId, String language, String region, boolean guest) {
        Artwork artwork = findArtwork(artworkId);
        String lang = normalizeLanguage(language);
        String source = artwork.getDescription().trim();
        String sourceHash = sha256(source);

        // The gallery text is English, so English needs no translation and no Gemini call at all
        if (lang.equals("en")) {
            return new StoryText(artworkId, lang, source);
        }

        return translationRepository.findFirstByArtworkIdAndLanguageAndSourceHash(artworkId, lang, sourceHash)
                .map(t -> new StoryText(artworkId, lang, t.getText()))
                .orElseGet(() -> {
                    if (guest) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                "Sign up to translate stories into this language");
                    }
                    return translateAndCache(artworkId, lang, source, sourceHash, region);
                });
    }

    public StoryAudio getStoryAudio(String artworkId, String language, String voice) {
        return getStoryAudio(artworkId, language, voice, null);
    }

    public StoryAudio getStoryAudio(String artworkId, String language, String voice, String region) {
        return getStoryAudio(artworkId, language, voice, region, false);
    }

    // guest=true: serve only already-cached audio; guests never trigger Gemini generation.
    public StoryAudio getStoryAudio(String artworkId, String language, String voice, String region, boolean guest) {
        StoryText storyText = getStoryText(artworkId, language, region, guest);
        if (storyText.text().length() > MAX_TTS_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Story is too long to read aloud");
        }
        String lang = storyText.language();
        String selectedVoice = (voice == null || voice.isBlank()) ? defaultVoice : voice.trim();
        String sourceHash = sha256(storyText.text());

        return audioRepository.findFirstByArtworkIdAndLanguageAndVoiceAndSourceHash(artworkId, lang, selectedVoice, sourceHash)
                .orElseGet(() -> {
                    if (guest) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                "Sign up to listen to this story");
                    }
                    return synthesizeAndCache(artworkId, lang, selectedVoice, storyText.text(), sourceHash, region);
                });
    }

    private StoryText translateAndCache(String artworkId, String lang, String source, String sourceHash, String region) {
        requireGemini();
        requireRegionAllowed(region);
        String translated = geminiService.translate(source, LANGUAGE_NAMES.getOrDefault(lang, lang));
        StoryTranslation saved = translationRepository.save(StoryTranslation.builder()
                .artworkId(artworkId)
                .language(lang)
                .sourceHash(sourceHash)
                .text(translated)
                .createdAt(Instant.now())
                .build());
        return new StoryText(artworkId, lang, saved.getText());
    }

    private StoryAudio synthesizeAndCache(String artworkId, String lang, String voice, String text, String sourceHash, String region) {
        requireGemini();
        requireRegionAllowed(region);
        byte[] pcm = geminiService.synthesize(text, voice);
        byte[] wav = wrapPcmInWav(pcm);
        return audioRepository.save(StoryAudio.builder()
                .artworkId(artworkId)
                .language(lang)
                .voice(voice)
                .sourceHash(sourceHash)
                .data(wav)
                .contentType("audio/wav")
                .createdAt(Instant.now())
                .build());
    }

    // Serving already-cached content never touches Gemini, so only generation is gated here.
    private void requireRegionAllowed(String region) {
        if (region == null || region.isBlank()
                || GEMINI_RESTRICTED_REGIONS.contains(region.trim().toUpperCase(java.util.Locale.ROOT))) {
            // Same 503 the app already handles: it reads the story aloud with on-device TTS instead
            throw new GeminiService.GeminiQuotaException();
        }
    }

    private void requireGemini() {
        if (!geminiService.isEnabled()) {
            // Key not configured on this deployment; the app falls back to on-device TTS
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Story translation is not configured");
        }
    }

    private Artwork findArtwork(String artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Artwork not found"));
        if (artwork.getDescription() == null || artwork.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artwork has no story");
        }
        return artwork;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        // "hi-IN" -> "hi", so regional variants share one cache entry
        return language.trim().toLowerCase().split("[-_]")[0];
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // Gemini TTS returns raw 24 kHz 16-bit mono PCM; a WAV header makes it playable by every player
    static byte[] wrapPcmInWav(byte[] pcm) {
        int sampleRate = 24000;
        int dataSize = pcm.length;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            out.write(intLE(36 + dataSize));
            out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
            out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
            out.write(intLE(16));          // PCM header size
            out.write(shortLE(1));         // PCM format
            out.write(shortLE(1));         // mono
            out.write(intLE(sampleRate));
            out.write(intLE(sampleRate * 2)); // byte rate
            out.write(shortLE(2));         // block align
            out.write(shortLE(16));        // bits per sample
            out.write("data".getBytes(StandardCharsets.US_ASCII));
            out.write(intLE(dataSize));
            out.write(pcm);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] intLE(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    private static byte[] shortLE(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8)};
    }

    public record StoryText(String artworkId, String language, String text) {
    }
}
