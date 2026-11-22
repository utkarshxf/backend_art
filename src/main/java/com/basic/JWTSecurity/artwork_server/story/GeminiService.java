package com.basic.JWTSecurity.artwork_server.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

// Talks to the Gemini Developer API (Google AI Studio free tier): translates artwork story text and
// synthesizes speech. Only public artwork stories are ever sent - never user data. A missing key only
// disables story translation/audio: /story/** then answers 503 and the app falls back to on-device TTS.
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.translate-model:gemini-3.1-flash-lite}")
    private String translateModel;

    @Value("${gemini.tts-model:gemini-2.5-flash-preview-tts}")
    private String ttsModel;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Translates text into targetLanguage, keeping names and artwork terms intact.
    public String translate(String text, String targetLanguage) {
        String prompt = "Translate the following artwork story into natural, modern " + targetLanguage + ". "
                + "Keep artist names, place names and art terms accurate. Return only the translated text, "
                + "no notes or commentary.\n\n" + text;
        ObjectNode body = textRequest(prompt);
        JsonNode response = post(translateModel, body);
        String translated = extractText(response);
        if (translated == null || translated.isBlank()) {
            throw new GeminiException("Gemini returned no translated text");
        }
        return translated.trim();
    }

    // Synthesizes speech for the given text and prebuilt voice. Returns raw 24 kHz 16-bit mono PCM.
    public byte[] synthesize(String text, String voice) {
        ObjectNode body = textRequest(text);
        ObjectNode speechConfig = body.putObject("generationConfig");
        speechConfig.putArray("responseModalities").add("AUDIO");
        speechConfig.putObject("speechConfig")
                .putObject("voiceConfig")
                .putObject("prebuiltVoiceConfig")
                .put("voiceName", voice);
        JsonNode response = post(ttsModel, body);
        JsonNode inlineData = response.path("candidates").path(0).path("content").path("parts").path(0).path("inlineData");
        String base64 = inlineData.path("data").asText(null);
        if (base64 == null || base64.isBlank()) {
            throw new GeminiException("Gemini returned no audio");
        }
        return Base64.getDecoder().decode(base64);
    }

    private ObjectNode textRequest(String text) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", text);
        return body;
    }

    private String extractText(JsonNode response) {
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            sb.append(part.path("text").asText(""));
        }
        return sb.toString();
    }

    private JsonNode post(String model, ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + model + ":generateContent?key=" + apiKey))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GeminiException("Gemini request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Gemini request interrupted");
        }
        if (response.statusCode() == 429) {
            // Free-tier daily/minute quota used up; the app falls back to on-device TTS until it resets
            throw new GeminiQuotaException();
        }
        if (response.statusCode() != 200) {
            log.warn("Gemini {} answered {}: {}", model, response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(300, response.body().length())));
            throw new GeminiException("Gemini answered " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new GeminiException("Unreadable Gemini response");
        }
    }

    // Something went wrong calling Gemini (network, error status, empty result)
    public static class GeminiException extends RuntimeException {
        public GeminiException(String message) {
            super(message);
        }
    }

    // Free-tier quota exhausted (HTTP 429); retry makes sense after the quota resets
    public static class GeminiQuotaException extends GeminiException {
        public GeminiQuotaException() {
            super("quota");
        }
    }
}
