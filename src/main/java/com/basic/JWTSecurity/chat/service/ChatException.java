package com.basic.JWTSecurity.chat.service;

import org.springframework.http.HttpStatus;

/** A chat request that cannot be served; ChatApi answers it as {@code {"message": ..., "status": false}}. */
public class ChatException extends RuntimeException {

    public static final String NOT_CONFIGURED = "Chat is not configured yet";

    private final HttpStatus status;

    public ChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** No service account: the backend cannot sign in chat users or send pushes. */
    public static ChatException notConfigured() {
        return new ChatException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }

    public static ChatException badRequest(String message) {
        return new ChatException(HttpStatus.BAD_REQUEST, message);
    }

    public static ChatException forbidden(String message) {
        return new ChatException(HttpStatus.FORBIDDEN, message);
    }

    public static ChatException notFound(String message) {
        return new ChatException(HttpStatus.NOT_FOUND, message);
    }

    /** Google (OAuth, Firestore or FCM) could not be reached or refused the backend. */
    public static ChatException upstream() {
        return new ChatException(HttpStatus.BAD_GATEWAY, "Chat is temporarily unavailable, please try again");
    }
}
