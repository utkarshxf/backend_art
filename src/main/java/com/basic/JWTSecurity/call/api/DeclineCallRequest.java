package com.basic.JWTSecurity.call.api;

/** Optional body of POST /chat/call/{id}/decline: {@code busy} when the phone declined because it is on another call. */
public record DeclineCallRequest(Boolean busy) {
}
