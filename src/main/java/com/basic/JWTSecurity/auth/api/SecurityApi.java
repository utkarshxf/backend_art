package com.basic.JWTSecurity.auth.api;


import com.basic.JWTSecurity.auth.service.ProfileService;
import com.basic.JWTSecurity.auth.model.JwtRequest;
import com.basic.JWTSecurity.auth.model.JwtResponse;
import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.model.TokenRequest;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController("/security")
public class SecurityApi {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ProfileService profileService;

    @PostMapping("/check")
    public Boolean isValidToken(@RequestBody TokenRequest token) {
        if (token == null) {
            return false;
        }
        try {
            System.out.println("Token received: " + token);
            profileService.checkToken(token);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody Profile user) {
        try {
            Profile registeredUser = profileService.registerUser(user);

            Authentication authentication;
            authentication = authenticationManager
                        .authenticate(new UsernamePasswordAuthenticationToken(registeredUser.getUsername(), registeredUser.getPassword()));


            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            JwtResponse response = new JwtResponse(jwtToken , userDetails.getUsername(), roles);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody JwtRequest loginRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        } catch (AuthenticationException exception) {
            Map<String, Object> map = new HashMap<>();
            map.put("error", "Bad credentials");
            map.put("status", false);
            return new ResponseEntity<Object>(map, HttpStatus.NOT_FOUND);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        JwtResponse response = new JwtResponse(jwtToken , userDetails.getUsername(), roles);

        return ResponseEntity.ok(response);
    }
}
