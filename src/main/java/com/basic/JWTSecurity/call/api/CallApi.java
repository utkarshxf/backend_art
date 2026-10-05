package com.basic.JWTSecurity.call.api;

import com.basic.JWTSecurity.call.service.CallConfig;
import com.basic.JWTSecurity.call.service.CallException;
import com.basic.JWTSecurity.call.service.CallService;
import com.basic.JWTSecurity.call.service.CallSession;
import com.basic.JWTSecurity.chat.service.ChatException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audio / video calls in chat. Every endpoint needs the app JWT (SecurityConfig: anyRequest().authenticated()) and
 * acts only as its subject, the username. Errors are {@code {"message": ..., "status": false}}; a 409 also carries
 * {@code callStatus}, where the call stands now; 503 means the backend has no Agora keys yet.
 */
@RestController
@RequestMapping("/chat/call")
public class CallApi {

    private final CallService calls;

    public CallApi(CallService calls) {
        this.calls = calls;
    }

    /** Whether calls can be placed, and the public Agora App ID. Also a cheap way to wake the backend. */
    @GetMapping("/config")
    public CallConfig config() {
        return calls.config();
    }

    /** The caller rings {@code callee}. */
    @PostMapping("/start")
    public ResponseEntity<CallSession> start(@RequestBody StartCallRequest request, Principal principal) {
        return withToken(calls.start(user(principal), request.callee(), request.kind()));
    }

    /** The callee answers. */
    @PostMapping("/{callId}/accept")
    public ResponseEntity<CallSession> accept(@PathVariable String callId, Principal principal) {
        return withToken(calls.accept(user(principal), callId));
    }

    /** A fresh token for a call in progress. */
    @PostMapping("/{callId}/token")
    public ResponseEntity<CallSession> token(@PathVariable String callId, Principal principal) {
        return withToken(calls.token(user(principal), callId));
    }

    /** The callee declines; {@code {"busy": true}} when their phone is on another call. */
    @PostMapping("/{callId}/decline")
    public CallSession decline(@PathVariable String callId, @RequestBody(required = false) DeclineCallRequest request,
                               Principal principal) {
        return calls.decline(user(principal), callId, request != null && Boolean.TRUE.equals(request.busy()));
    }

    /** The caller gives up before an answer; {@code {"timeout": true}} when nobody picked up in time. */
    @PostMapping("/{callId}/cancel")
    public CallSession cancel(@PathVariable String callId, @RequestBody(required = false) CancelCallRequest request,
                              Principal principal) {
        return calls.cancel(user(principal), callId, request != null && Boolean.TRUE.equals(request.timeout()));
    }

    /** Either side hangs up. */
    @PostMapping("/{callId}/end")
    public CallSession end(@PathVariable String callId, Principal principal) {
        return calls.end(user(principal), callId);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> callError(ChatException e) {
        // the shared Google clients speak of chat; to the app this is a call that could not be served
        String message = e.getStatus() == HttpStatus.BAD_GATEWAY
                ? "Calls are temporarily unavailable, please try again" : e.getMessage();
        Map<String, Object> body = error(message);
        if (e instanceof CallException call && call.getCallStatus() != null) {
            body.put("callStatus", call.getCallStatus());
        }
        return new ResponseEntity<>(body, e.getStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException e) {
        return new ResponseEntity<>(error("Invalid request body"), HttpStatus.BAD_REQUEST);
    }

    private static String user(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            // unreachable behind SecurityConfig, but never act for an unknown user
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return principal.getName();
    }

    /** Answers that carry a token are never cached. */
    private static ResponseEntity<CallSession> withToken(CallSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", false);
        return body;
    }
}
