package com.basic.JWTSecurity.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Basic rate limiting for anonymous (no-JWT) traffic on the guest-readable endpoints.
// Authenticated requests are never limited here. Per-IP sliding one-minute window,
// in-memory (single instance; resets on deploy, which is fine for a basic cap).
public class AnonymousRateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_MINUTE = 60;
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_IPS = 10_000;

    private static final class Window {
        long windowStart;
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Requests carrying a JWT attempt are not anonymous traffic; let them through untouched
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_IPS) {
            evictExpired(now);
        }
        Window window = windows.computeIfAbsent(ip, k -> {
            Window w = new Window();
            w.windowStart = now;
            return w;
        });
        synchronized (window) {
            if (now - window.windowStart >= WINDOW_MS) {
                window.windowStart = now;
                window.count = 0;
            }
            window.count++;
            if (window.count > LIMIT_PER_MINUTE) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Too many requests, slow down\",\"status\":false}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().windowStart >= WINDOW_MS * 2) {
                it.remove();
            }
        }
    }
}
