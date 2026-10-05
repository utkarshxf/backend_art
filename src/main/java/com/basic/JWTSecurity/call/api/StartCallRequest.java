package com.basic.JWTSecurity.call.api;

/** Body of POST /chat/call/start: who to call and how ({@code "audio"} or {@code "video"}). */
public record StartCallRequest(String callee, String kind) {
}
