package com.basic.JWTSecurity.artwork_server.story;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

// Azure AI Speech (F0 free tier: 0.5M neural characters/month) as the primary narration voice;
// Gemini TTS stays as the fallback when Azure is not configured or its quota/errors bite.
// Returns ready-to-serve WAV (riff-24khz-16bit-mono-pcm), the same format the Gemini path produces.
// Only public artwork stories are ever sent - never user data.
@Service
@Slf4j
public class AzureSpeechService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${azure.speech.key:}")
    private String speechKey;

    @Value("${azure.speech.region:}")
    private String speechRegion;

    // One pleasant default neural voice per story language (Swara/Madhur/Neerja chosen by the app owner).
    private static final Map<String, String> DEFAULT_VOICES = Map.of(
            "en", "en-IN-NeerjaNeural",
            "hi", "hi-IN-SwaraNeural",
            "tr", "tr-TR-EmelNeural",
            "fa", "fa-IR-DilaraNeural",
            "ar", "ar-SA-ZariyahNeural",
            "id", "id-ID-GadisNeural",
            "pl", "pl-PL-ZofiaNeural",
            "hu", "hu-HU-NoemiNeural",
            "el", "el-GR-AthinaNeural"
    );

    public boolean isEnabled() {
        return speechKey != null && !speechKey.isBlank() && speechRegion != null && !speechRegion.isBlank();
    }

    // Synthesizes the story with the language's default Azure neural voice. Returns WAV bytes.
    public byte[] synthesize(String text, String lang) {
        String voice = DEFAULT_VOICES.get(lang);
        if (voice == null) {
            throw new AzureSpeechException("No Azure voice mapped for language " + lang);
        }
        String ssml = "<speak version='1.0' xml:lang='" + localeOf(voice) + "'>"
                + "<voice name='" + voice + "'>" + escapeXml(text) + "</voice></speak>";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + speechRegion + ".tts.speech.microsoft.com/cognitiveservices/v1"))
                .timeout(Duration.ofSeconds(120))
                .header("Ocp-Apim-Subscription-Key", speechKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
                .header("User-Agent", "artly-backend")
                .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new AzureSpeechException("Azure Speech request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AzureSpeechException("Azure Speech request interrupted");
        }
        if (response.statusCode() == 429 || response.statusCode() == 403) {
            // Free-tier monthly quota used up (or key disabled); fall back to Gemini TTS
            throw new AzureQuotaException();
        }
        if (response.statusCode() != 200) {
            String body = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            log.warn("Azure Speech answered {}: {}", response.statusCode(),
                    body.substring(0, Math.min(300, body.length())));
            throw new AzureSpeechException("Azure Speech answered " + response.statusCode());
        }
        return response.body();
    }

    private static String localeOf(String voice) {
        int secondDash = voice.indexOf('-', voice.indexOf('-') + 1);
        return secondDash > 0 ? voice.substring(0, secondDash) : voice;
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // Something went wrong calling Azure Speech (network, error status, unmapped language)
    public static class AzureSpeechException extends RuntimeException {
        public AzureSpeechException(String message) {
            super(message);
        }
    }

    // Free-tier monthly quota exhausted (HTTP 429/403); Gemini TTS takes over until it resets
    public static class AzureQuotaException extends AzureSpeechException {
        public AzureQuotaException() {
            super("quota");
        }
    }
}
