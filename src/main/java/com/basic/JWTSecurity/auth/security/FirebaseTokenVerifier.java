package com.basic.JWTSecurity.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies Firebase Auth ID tokens (RS256, signed with Google's rotating keys) and returns the phone number the
 * user proved ownership of with the SMS code. The app must never be trusted to send a phone number on its own.
 */
@Component
public class FirebaseTokenVerifier {

    private static final String CERTS_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final String projectId;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysExpireAt = Instant.EPOCH;
    private volatile Instant lastFetch = Instant.EPOCH;

    public FirebaseTokenVerifier(@Value("${firebase.project-id}") String projectId, ObjectMapper objectMapper) {
        this.projectId = projectId;
        this.objectMapper = objectMapper;
    }

    /** Who the token proves the user is: a phone number (SMS sign-in) and/or a verified email (Google sign-in). */
    public record VerifiedIdentity(String uid, String phone, String email) {}

    /** Returns the verified phone number (E.164, e.g. +919876543210) or throws {@link InvalidTokenException}. */
    public String verifiedPhone(String idToken) {
        String phone = verify(idToken).phone();
        if (phone == null) {
            throw new InvalidTokenException("Phone verification is required");
        }
        return phone;
    }

    /** Verifies the token; it must prove a phone number or a verified email, otherwise {@link InvalidTokenException}. */
    public VerifiedIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidTokenException("Phone verification is required");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(header -> publicKey(((ProtectedHeader) header).getKeyId()))
                    .requireIssuer("https://securetoken.google.com/" + projectId)
                    .requireAudience(projectId)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException | ClassCastException e) {
            throw new InvalidTokenException("Phone verification is invalid or expired");
        }
        String phone = blankToNull(claims.get("phone_number", String.class));
        String email = Boolean.TRUE.equals(claims.get("email_verified", Boolean.class))
                ? blankToNull(claims.get("email", String.class)) : null;
        if (claims.getSubject() == null || claims.getSubject().isBlank() || (phone == null && email == null)) {
            throw new InvalidTokenException("Phone verification is invalid or expired");
        }
        return new VerifiedIdentity(claims.getSubject(), phone, email == null ? null : email.toLowerCase());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private PublicKey publicKey(String kid) {
        // the key maps are immutable and throw on a null key, which would surface as a 500
        if (kid == null || kid.isBlank()) {
            throw new InvalidTokenException("Phone verification is invalid or expired");
        }
        Map<String, PublicKey> current = keys;
        boolean stale = Instant.now().isAfter(keysExpireAt);
        // an unknown kid usually means Google rotated keys; refetch, but at most once a minute
        boolean unknown = !current.containsKey(kid) && Instant.now().isAfter(lastFetch.plusSeconds(60));
        if (stale || unknown) {
            current = refreshKeys();
        }
        PublicKey key = current.get(kid);
        if (key == null) {
            throw new InvalidTokenException("Phone verification is invalid or expired");
        }
        return key;
    }

    private synchronized Map<String, PublicKey> refreshKeys() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(CERTS_URL)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            Map<String, String> pems = objectMapper.readValue(response.body(), new TypeReference<Map<String, String>>() {});
            CertificateFactory certificates = CertificateFactory.getInstance("X.509");
            Map<String, PublicKey> fresh = new HashMap<>();
            for (Map.Entry<String, String> e : pems.entrySet()) {
                fresh.put(e.getKey(), certificates.generateCertificate(
                        new ByteArrayInputStream(e.getValue().getBytes(StandardCharsets.US_ASCII))).getPublicKey());
            }
            long maxAge = response.headers().firstValue("Cache-Control")
                    .map(MAX_AGE::matcher).filter(Matcher::find).map(m -> Long.parseLong(m.group(1))).orElse(3600L);
            keys = Map.copyOf(fresh);
            keysExpireAt = Instant.now().plusSeconds(maxAge);
            lastFetch = Instant.now();
            return keys;
        } catch (Exception e) {
            lastFetch = Instant.now();
            throw new InvalidTokenException("Could not check phone verification, please try again");
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
