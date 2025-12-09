package com.basic.JWTSecurity.auth.service;

import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.model.TokenRequest;
import com.basic.JWTSecurity.auth.repository.ProfileRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

@Service
public class ProfileServiceImpl implements UserDetailsService, ProfileService {

    @Autowired
    private ProfileRepository profileRepository;

    @Override
    public Profile changeUserPassword(String phoneNumber, String newPassword) {
        Optional<Profile> userOpt = profileRepository.findByPhone(phoneNumber);
        if (!userOpt.isPresent()) {
            throw new UsernameNotFoundException("User not found");
        }
        Profile profile = userOpt.get();
        profile.setPassword(passwordEncoder().encode(newPassword));
        Profile user1 = profileRepository.save(profile);
        return user1;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<Profile> userOpt = profileRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            throw new UsernameNotFoundException("User not found");
        }

        Profile profile = userOpt.get();


        return User.withUsername(profile.getUsername())
                .password(profile.getPassword())
                .roles("USER")
                .build();
    }

    @Override
    public Profile registerUser(Profile user) {
        if (profileRepository.findByUsername(user.getUsername().toLowerCase()).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        if (profileRepository.findByPhone(user.getPhone()).isPresent()) {
            throw new RuntimeException("Phone number already exists");
        }
        String userPassword = user.getPassword();
        user.setPassword(passwordEncoder().encode(user.getPassword()));
        Profile user1 =  profileRepository.save(user);
        user1.setPassword(userPassword);
        return user1;
    }

    @Override
    public Claims checkToken(TokenRequest token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token.token)
                .getPayload();
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        return !profileRepository.findByUsername(username).isPresent();
    }

    @Override
    public Map<String, Object> validateUsername(String username) {
        Map<String, Object> response = new HashMap<>();

        // Check if username is null or empty
        if (username == null || username.trim().isEmpty()) {
            response.put("isValid", false);
            response.put("message", "Username cannot be empty");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check for spaces
        if (username.contains(" ")) {
            response.put("isValid", false);
            response.put("message", "Username cannot contain spaces");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check minimum length (at least 3 characters)
        if (username.length() < 3) {
            response.put("isValid", false);
            response.put("message", "Username must be at least 3 characters long");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check maximum length (at most 20 characters)
        if (username.length() > 20) {
            response.put("isValid", false);
            response.put("message", "Username must not exceed 20 characters");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check if username contains only alphanumeric characters and underscores
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            response.put("isValid", false);
            response.put("message", "Username can only contain letters, numbers, and underscores");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check if username starts with a letter (not a number or underscore)
        if (!Character.isLetter(username.charAt(0))) {
            response.put("isValid", false);
            response.put("message", "Username must start with a letter");
            response.put("username", username);
            response.put("status", true);
            return response;
        }

        // Check if username is available in the database
        boolean isAvailable = isUsernameAvailable(username);
        response.put("isValid", isAvailable);
        response.put("username", username);
        response.put("status", true);

        if (!isAvailable) {
            response.put("message", "Username is already taken");
        }

        return response;
    }

    @Value("${spring.app.jwtSecret}")
    private String secretKey;
    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }


    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
