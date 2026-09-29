package com.basic.JWTSecurity.chat.api;

/** Body of POST /chat/notify: the message the caller just wrote to Firestore. */
public record ChatNotifyRequest(String conversationId, String messageId) {
}
