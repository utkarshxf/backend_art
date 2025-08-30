package com.basic.JWTSecurity.artwork_server.config;

import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Beans {
    @Bean
    org.neo4j.cypherdsl.core.renderer.Configuration cypherDslConfiguration() {
        return org.neo4j.cypherdsl.core.renderer.Configuration.newConfig()
                .withDialect(Dialect.NEO4J_5).build();
    }
    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(
                "neo4j+s://eeb6bc24.databases.neo4j.io:7687",
                AuthTokens.basic("neo4j", "cfe0IG8pV2fvQHP3cZ1IDBU_kwBKDNNzNgxNGOad8b0")
        );
    }

}
