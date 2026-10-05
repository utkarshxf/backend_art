package com.basic.JWTSecurity.call.service;

/**
 * Answer of GET /chat/call/config: whether calls can be placed, the public Agora App ID (null when they cannot) and
 * how long a call rings.
 */
public record CallConfig(boolean enabled, String appId, int ringTimeoutSec) {
}
