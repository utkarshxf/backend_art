package com.basic.JWTSecurity.artwork_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps the free-tier databases from being paused. Neo4j Aura Free pauses an instance after 72 hours without a
 * write; MongoDB Atlas M0 pauses a cluster after 60 days without a connection.
 * <p>
 * GET /health pings both databases (an external scheduler calls it every few minutes, which also keeps the Azure
 * app from idling out); the job below adds the Neo4j write, since a read alone may not count as activity there.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseKeepAliveService {

    private final Driver driver;
    private final MongoTemplate mongoTemplate;

    // A minute after startup, then every 6 hours: a few missed runs (restarts, deploys) still stay well within 72 hours
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    public void keepDatabasesAlive() {
        try {
            writeNeo4jHeartbeat();
            pingMongo();
            log.info("Database keep-alive done");
        } catch (Exception e) {
            log.error("Database keep-alive failed", e);
        }
    }

    public void pingMongo() {
        mongoTemplate.executeCommand(new Document("ping", 1));
    }

    public void pingNeo4j() {
        try (Session session = driver.session()) {
            session.run("RETURN 1").consume();
        }
    }

    // A single :KeepAlive node, updated in place
    private void writeNeo4jHeartbeat() {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MERGE (k:KeepAlive {id: 'keepalive'}) SET k.lastSeenAt = datetime()").consume());
        }
    }
}
