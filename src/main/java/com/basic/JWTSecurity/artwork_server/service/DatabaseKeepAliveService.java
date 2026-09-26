package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseKeepAliveService {

    private final ArtworkRepository artworkRepository;

    @Scheduled(cron = "0 0 0 * * ?") // Runs at midnight every day
    public void keepDatabaseAlive() {
        try {
            log.info("Starting daily database keep-alive operation...");
            // Fetch a small sample of artworks to keep the connection alive
            var artwork = artworkRepository.todayBiggestHit();
            log.info("Successfully fetched artwork with ID {} in keep-alive operation", artwork.getId());
        } catch (Exception e) {
            log.error("Error in database keep-alive operation: {}", e.getMessage());
        }
    }
}
