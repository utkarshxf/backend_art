package com.basic.JWTSecurity.call.service;

import com.basic.JWTSecurity.chat.service.ChatException;
import org.springframework.http.HttpStatus;

/**
 * A call request that cannot be served, on top of {@link ChatException}: 503 while the backend has no Agora keys,
 * and 409 for a step the call can no longer take, which also tells the app where the call stands now.
 */
public class CallException extends ChatException {

    public static final String NOT_CONFIGURED = "Calls are not configured yet";

    private final String callStatus;

    private CallException(HttpStatus status, String message, String callStatus) {
        super(status, message);
        this.callStatus = callStatus;
    }

    /** The call's current status for a 409, otherwise null. */
    public String getCallStatus() {
        return callStatus;
    }

    /** No Agora App ID / App Certificate (or no Firebase service account): calls cannot be placed. */
    public static CallException notConfigured() {
        return new CallException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED, null);
    }

    /** The call is in {@code callStatus}, which rules the requested step out (e.g. answering a call that is over). */
    public static CallException conflict(String message, String callStatus) {
        return new CallException(HttpStatus.CONFLICT, message, callStatus);
    }
}
