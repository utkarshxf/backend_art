package com.basic.JWTSecurity.artwork_server.config;

import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
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
    public Driver neo4jDriver(@Value("${spring.neo4j.uri}") String uri,
                              @Value("${spring.neo4j.authentication.username}") String username,
                              @Value("${spring.neo4j.authentication.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

}
