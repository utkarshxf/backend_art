package com.basic.JWTSecurity.chat.api;

import com.basic.JWTSecurity.chat.service.ChatException;
import com.basic.JWTSecurity.chat.service.ChatService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chat support for the app. Both endpoints need the app JWT (SecurityConfig: anyRequest().authenticated()) and act
 * only as its subject, the username. Errors are {@code {"message": ..., "status": false}}; 503 means the backend has
 * no Firebase service account yet.
 */
@RestController
@RequestMapping("/chat")
public class ChatApi {

    private final ChatService chatService;

    public ChatApi(ChatService chatService) {
        this.chatService = chatService;
    }

    /** A Firebase custom token whose uid is the caller's username; the app signs in to Firebase with it. */
    @PostMapping("/token")
    public ResponseEntity<ChatTokenResponse> token(Principal principal) {
        String username = caller(principal);
        String token = chatService.mintCustomToken(username);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ChatTokenResponse(token, username));
    }

    /** Push notification for a message the caller just wrote; the backend checks the message in Firestore first. */
    @PostMapping("/notify")
    public ChatNotifyResponse notifyRecipient(@RequestBody ChatNotifyRequest request, Principal principal) {
        int sent = chatService.notifyRecipient(caller(principal), request.conversationId(), request.messageId());
        return new ChatNotifyResponse(sent);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> chatError(ChatException e) {
        return error(e.getMessage(), e.getStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException e) {
        return error("Invalid request body", HttpStatus.BAD_REQUEST);
    }

    private static String caller(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            // unreachable behind SecurityConfig, but never act for an unknown user
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return principal.getName();
    }

    private static ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", false);
        return new ResponseEntity<>(body, status);
    }
}
