package com.basic.JWTSecurity.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where a call stands, as answered by every /chat/call endpoint. Starting, answering and refreshing also carry
 * what the app needs to join the Agora channel as {@code uid} ({@code channel}, {@code appId}, {@code token},
 * {@code expiresInSec}); starting adds how long the other phone rings. Fields that do not apply are left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallSession(String callId,
                          String status,
                          String kind,
                          String caller,
                          String callee,
                          String conversationId,
                          long durationSec,
                          String channel,
                          String appId,
                          String token,
                          Integer uid,
                          Integer expiresInSec,
                          Integer ringTimeoutSec) {

    @Override
    public String toString() {
        // the default record toString would include the token
        return "CallSession[" + callId + " " + status + "]";
    }
}
