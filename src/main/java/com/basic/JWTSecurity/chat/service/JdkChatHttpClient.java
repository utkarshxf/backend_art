package com.basic.JWTSecurity.chat.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link ChatHttpClient} on the JDK's HttpClient: no extra dependency, fine on a 1 GB App Service plan. */
@Component
public class JdkChatHttpClient implements ChatHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    @Override
    public Response send(Request request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url())).timeout(REQUEST_TIMEOUT);
        request.headers().forEach(builder::header);
        builder.method(request.method(), request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while calling " + request.url());
        }
    }
}
