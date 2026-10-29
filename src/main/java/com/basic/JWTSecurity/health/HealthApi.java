package com.basic.JWTSecurity.health;

import com.basic.JWTSecurity.artwork_server.service.DatabaseKeepAliveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public liveness check: 200 when both databases answer, 503 otherwise. The keep-alive workflow
 * (.github/workflows/keep-alive.yml) calls it every 5 minutes so the app and the databases never go idle.
 * Errors are only logged, never returned, since anyone can call this.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthApi {

    private final DatabaseKeepAliveService keepAliveService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("mongodb", check("MongoDB", keepAliveService::pingMongo));
        body.put("neo4j", check("Neo4j", keepAliveService::pingNeo4j));
        if (body.containsValue("DOWN")) {
            body.put("status", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private String check(String name, Runnable ping) {
        try {
            ping.run();
            return "UP";
        } catch (Exception e) {
            log.error("Health check: {} is down", name, e);
            return "DOWN";
        }
    }
}
