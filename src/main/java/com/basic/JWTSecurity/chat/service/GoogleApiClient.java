package com.basic.JWTSecurity.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authorized JSON calls to Google APIs as the service account; retries once with a fresh token after a 401. */
@Component
public class GoogleApiClient {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiClient.class);

    private final GoogleAccessTokenProvider tokens;
    private final ChatHttpClient http;

    public GoogleApiClient(GoogleAccessTokenProvider tokens, ChatHttpClient http) {
        this.tokens = tokens;
        this.http = http;
    }

    /** Sends the request; a network failure is a {@link ChatException#upstream()}, any HTTP status is returned. */
    public ChatHttpClient.Response send(String method, String url, String jsonBody) {
        for (int attempt = 1; ; attempt++) {
            String token = tokens.accessToken();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + token);
            if (jsonBody != null) {
                headers.put("Content-Type", "application/json; charset=UTF-8");
            }
            ChatHttpClient.Response response;
            try {
                response = http.send(new ChatHttpClient.Request(method, url, headers, jsonBody));
            } catch (IOException e) {
                logger.warn("{} {} failed: {}", method, url, e.toString());
                throw ChatException.upstream();
            }
            if (response.status() == 401 && attempt == 1) {
                // revoked or expired early: fetch a new token and try once more
                tokens.invalidate(token);
                continue;
            }
            return response;
        }
    }
}
