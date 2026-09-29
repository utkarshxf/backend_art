package com.basic.JWTSecurity.chat.api;

/** Answer of POST /chat/notify: how many of the recipient's devices FCM accepted the push for. */
public record ChatNotifyResponse(int sent) {
}
