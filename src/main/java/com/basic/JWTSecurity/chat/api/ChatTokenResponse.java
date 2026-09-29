package com.basic.JWTSecurity.chat.api;

/** Answer of POST /chat/token: a Firebase custom token for signInWithCustomToken, and the uid it signs in as. */
public record ChatTokenResponse(String token, String uid) {
}
