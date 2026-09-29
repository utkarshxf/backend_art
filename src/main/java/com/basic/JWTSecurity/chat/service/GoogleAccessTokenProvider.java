package com.basic.JWTSecurity.chat.service;

import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials;
import com.basic.JWTSecurity.chat.config.ChatFirebaseCredentials.ServiceAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * OAuth access tokens for the service account (JWT-bearer grant), shared by the Firestore and FCM calls and cached
 * until about five minutes before they expire.
 */
@Component
public class GoogleAccessTokenProvider {

    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String SCOPES =
            "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);

    private static final Logger logger = LoggerFactory.getLogger(GoogleAccessTokenProvider.class);

    private final ChatFirebaseCredentials credentials;
    private final ChatHttpClient http;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private String cachedToken;
    private Instant refreshAt = Instant.EPOCH;

    @Autowired
    public GoogleAccessTokenProvider(ChatFirebaseCredentials credentials, ChatHttpClient http, ObjectMapper objectMapper) {
        this(credentials, http, objectMapper, Clock.systemUTC());
    }

    GoogleAccessTokenProvider(ChatFirebaseCredentials credentials, ChatHttpClient http, ObjectMapper objectMapper, Clock clock) {
        this.credentials = credentials;
        this.http = http;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * A valid access token, fetched when there is none or it is about to expire. Synchronized so that concurrent
     * requests wait for one refresh instead of each asking Google.
     */
    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(refreshAt)) {
            return cachedToken;
        }
        ServiceAccount account = credentials.serviceAccount().orElseThrow(ChatException::notConfigured);
        String assertion = Jwts.builder()
                .header().type("JWT").keyId(account.privateKeyId()).and()
                .issuer(account.clientEmail())
                .audience().single(TOKEN_URL)
                .claim("scope", SCOPES)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(account.privateKey(), Jwts.SIG.RS256)
                .compact();
        String form = "grant_type=" + URLEncoder.encode(JWT_BEARER, StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

        ChatHttpClient.Response response;
        try {
            response = http.send(new ChatHttpClient.Request("POST", TOKEN_URL,
                    Map.of("Content-Type", "application/x-www-form-urlencoded"), form));
        } catch (IOException e) {
            logger.warn("Google OAuth token request failed: {}", e.toString());
            throw ChatException.upstream();
        }
        JsonNode json = readJson(response.body());
        String token = json.path("access_token").asText("");
        if (!response.isSuccess() || token.isEmpty()) {
            // e.g. invalid_grant when the key was deleted in the console; the error fields hold no secrets
            logger.error("Google OAuth token request failed: HTTP {} {} {}", response.status(),
                    json.path("error").asText(""), json.path("error_description").asText(""));
            throw ChatException.upstream();
        }
        long expiresIn = json.path("expires_in").asLong(3600);
        cachedToken = token;
        refreshAt = now.plusSeconds(expiresIn).minus(REFRESH_BEFORE_EXPIRY);
        return token;
    }

    /** Drops {@code token} after Google answered 401 to it, so that the next call fetches a new one. */
    public synchronized void invalidate(String token) {
        if (token != null && token.equals(cachedToken)) {
            cachedToken = null;
        }
    }

    private JsonNode readJson(String body) {
        try {
            JsonNode node = body == null || body.isBlank() ? null : objectMapper.readTree(body);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }
}
