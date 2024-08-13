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

@Service
public class ProfileServiceImpl implements UserDetailsService, ProfileService {

    @Autowired
    private ProfileRepository profileRepository;



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
        if (profileRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        user.setPassword(passwordEncoder().encode(user.getPassword()));
        return profileRepository.save(user);
    }

    @Override
    public Claims checkToken(TokenRequest token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token.token)
                .getPayload();
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
