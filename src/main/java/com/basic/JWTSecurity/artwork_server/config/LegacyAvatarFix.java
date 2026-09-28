package com.basic.JWTSecurity.artwork_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Old app versions stored default avatars on a CloudFront host that no longer exists, so those pictures never load.
// Point them at a generated initials avatar (what new users get). Idempotent: once fixed, nothing matches.
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyAvatarFix implements ApplicationRunner {

    private static final String DEAD_HOST = "https://dxvnlnyzij172.cloudfront.net/";

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        try (Session session = driver.session()) {
            long users = session.run("""
                    MATCH (u:User) WHERE u.profilePicture STARTS WITH $deadHost
                    SET u.profilePicture = 'https://ui-avatars.com/api/?name=' + replace(coalesce(u.name, u.id, 'User'), ' ', '+')
                    RETURN count(u) AS n
                    """, java.util.Map.of("deadHost", DEAD_HOST)).single().get("n").asLong();
            long artists = session.run("""
                    MATCH (a:Artist) WHERE a.image_url STARTS WITH $deadHost
                    SET a.image_url = 'https://ui-avatars.com/api/?name=' + replace(coalesce(a.name, a.id, 'Artist'), ' ', '+')
                    RETURN count(a) AS n
                    """, java.util.Map.of("deadHost", DEAD_HOST)).single().get("n").asLong();
            if (users + artists > 0) {
                log.info("Replaced dead CloudFront avatars: {} users, {} artists", users, artists);
            }
        } catch (Exception e) {
            log.warn("Could not fix legacy avatars: {}", e.getMessage());
        }
    }
}
