package com.basic.JWTSecurity.call.api;

/** Optional body of POST /chat/call/{id}/cancel: {@code timeout} when the caller's ring time ran out unanswered. */
public record CancelCallRequest(Boolean timeout) {
}
