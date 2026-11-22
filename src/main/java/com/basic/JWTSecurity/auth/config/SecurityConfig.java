package com.basic.JWTSecurity.auth.config;

import com.basic.JWTSecurity.auth.security.AccessDeniedHandlerJwt;
import com.basic.JWTSecurity.auth.security.AuthTokenFilter;
import com.basic.JWTSecurity.auth.security.AuthEntryPointJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public com.basic.JWTSecurity.auth.security.AnonymousRateLimitFilter anonymousRateLimitFilter() {
        return new com.basic.JWTSecurity.auth.security.AnonymousRateLimitFilter();
    }

    @Autowired
    private AccessDeniedHandlerJwt accessDeniedHandler;

    // Only catalogue-admin accounts may call /admin/**; any other signed-in user gets 403.
    // Comma-separated usernames via the APP_ADMIN_USERNAMES env var (matches the app setting Azure already had).
    @Bean
    AuthorizationManager<RequestAuthorizationContext> adminAuthorizationManager(
            @Value("${spring.app.adminUsernames:utkarshxf}") String adminUsernames) {
        java.util.Set<String> admins = new java.util.HashSet<>();
        for (String name : adminUsernames.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) admins.add(trimmed);
        }
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            boolean allowed = auth != null && auth.isAuthenticated()
                    && admins.contains(auth.getName());
            return new AuthorizationDecision(allowed);
        };
    }

    @Bean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                   AuthorizationManager<RequestAuthorizationContext> adminAuthorizationManager) throws Exception {
        http.authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/admin/**").access(adminAuthorizationManager)
                        // Called by the keep-alive workflow, which has no JWT
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // Guest browsing: read-only catalogue endpoints work without a JWT.
                        // Every write (like/comment/follow/upload) stays authenticated.
                        .requestMatchers(HttpMethod.GET,
                                "/artwork", "/artwork/popular", "/artwork/new-arrivals",
                                "/artwork/recommended-today", "/artwork/today-biggest-hit",
                                "/artwork/similarGenreArtworks", "/artwork/moreFromArtist",
                                "/artwork/recommend", "/artwork/*/*", "/artwork/*/stats",
                                "/genres", "/genres/all",
                                "/artist/*", "/artist/getArtistByArtistId", "/artist/getArtistStats",
                                "/artist/getArtworkByArtistId", "/artist/search",
                                "/comments/artwork/*", "/search", "/gallery",
                                "/story/*", "/story/*/audio").permitAll()
                        // Everything the app calls before it has a JWT: log in, sign up, reset the password,
                        // check a username while creating the account
                        .requestMatchers("/login", "/signup",
                                "/auth/firebase", "/auth/firebase/signup", "/auth/phone", "/auth/phone/signup",
                                "/forgetPassword", "/isValidUsername", "/users/isValidUsername",
                                "/check", "/error").permitAll()
                        .anyRequest().authenticated());
        // Let the @CrossOrigin settings answer browser preflight requests, which never carry a JWT
        http.cors(withDefaults());
        http.sessionManagement(
                session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)
        );
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedHandler)
                .accessDeniedHandler(accessDeniedHandler));
        //http.httpBasic(withDefaults());
        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions
                        .sameOrigin()
                )
        );
        http.csrf(csrf -> csrf.disable());
        http.addFilterBefore(anonymousRateLimitFilter(),
                UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationJwtTokenFilter(),
                UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration builder) throws Exception {
        return builder.getAuthenticationManager();
    }
}
