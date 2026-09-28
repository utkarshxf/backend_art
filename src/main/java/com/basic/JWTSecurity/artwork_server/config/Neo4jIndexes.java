package com.basic.JWTSecurity.artwork_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Every repository query looks nodes up by their id property; without an index each lookup scans the whole label.
@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jIndexes implements ApplicationRunner {

    private static final List<String> LABELS = List.of("Artwork", "User", "Artist", "Genre", "Comment", "Favorites", "Gallery");

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        try (Session session = driver.session()) {
            for (String label : LABELS) {
                session.run("CREATE INDEX " + label.toLowerCase() + "_id IF NOT EXISTS FOR (n:" + label + ") ON (n.id)").consume();
            }
            log.info("Neo4j id indexes ensured for {}", LABELS);
        } catch (Exception e) {
            log.warn("Could not ensure Neo4j id indexes: {}", e.getMessage());
        }
    }
}
