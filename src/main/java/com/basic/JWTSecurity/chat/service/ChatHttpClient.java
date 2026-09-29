package com.basic.JWTSecurity.chat.service;

import java.io.IOException;
import java.util.Map;

/** The few HTTP calls chat makes to Google (OAuth, Firestore REST, FCM), behind an interface so tests can fake them. */
public interface ChatHttpClient {

    Response send(Request request) throws IOException;

    record Request(String method, String url, Map<String, String> headers, String body) {
        public Request {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        @Override
        public String toString() {
            // headers carry the bearer token and bodies can carry tokens: keep them out of logs and test output
            return method + " " + url;
        }
    }

    record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }
}
