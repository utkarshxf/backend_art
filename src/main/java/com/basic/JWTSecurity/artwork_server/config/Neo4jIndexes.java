package com.basic.JWTSecurity.artwork_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.basic.JWTSecurity.artwork_server.service.SearchService;

import java.util.List;

// Every repository query looks nodes up by their id property; without an index each lookup scans the whole label.
@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jIndexes implements ApplicationRunner {

    private static final List<String> LABELS = List.of("Artwork", "User", "Artist", "Genre", "Comment", "Favorites", "Gallery");

    private static final String FOLDING = " OPTIONS {indexConfig: {`fulltext.analyzer`: 'standard-folding'}}";
    private static final List<String> FULLTEXT = List.of(
            "CREATE FULLTEXT INDEX " + SearchService.ARTWORK_INDEX + " IF NOT EXISTS FOR (n:Artwork) "
                    + "ON EACH [n.title, n.artist, n.medium, n.art_movement, n.description]",
            "CREATE FULLTEXT INDEX " + SearchService.ARTIST_INDEX + " IF NOT EXISTS FOR (n:Artist) "
                    + "ON EACH [n.name, n.nationality, n.art_movement]",
            "CREATE FULLTEXT INDEX " + SearchService.USER_INDEX + " IF NOT EXISTS FOR (n:User) "
                    + "ON EACH [n.id, n.name]"
    );

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        try (Session session = driver.session()) {
            for (String label : LABELS) {
                session.run("CREATE INDEX " + label.toLowerCase() + "_id IF NOT EXISTS FOR (n:" + label + ") ON (n.id)").consume();
            }
            log.info("Neo4j id indexes ensured for {}", LABELS);
            // Search (SearchService): accent-insensitive full-text indexes; without the folding analyzer if the
            // server doesn't offer it (search still works, just accent-sensitive)
            for (String statement : FULLTEXT) {
                try {
                    session.run(statement + FOLDING).consume();
                } catch (Exception e) {
                    log.warn("Full-text index with folding analyzer failed ({}); creating it without", e.getMessage());
                    session.run(statement).consume();
                }
            }
            log.info("Neo4j full-text search indexes ensured");
        } catch (Exception e) {
            log.warn("Could not ensure Neo4j id indexes: {}", e.getMessage());
        }
    }
}
