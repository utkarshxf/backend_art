package com.basic.JWTSecurity.auth.api;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.model.User;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import com.basic.JWTSecurity.auth.model.*;
import com.basic.JWTSecurity.auth.service.ProfileService;
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

import java.time.LocalDate;
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

    @Autowired
    private UserService userService;

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
            if (registeredUser == null) {
                Map<String, Object> map = new HashMap<>();
                map.put("message", "User registration failed");
                map.put("status", false);
                return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
            }

            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(registeredUser.getUsername(), registeredUser.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            JwtResponse response = new JwtResponse(jwtToken, userDetails.getUsername(), roles);

            String country = resolveCountry(registeredUser);

            userService.createUser(
                    new UserRegistrationRequestRecord(
                            false,
                            response.getUsername(),
                            response.getUsername(),
                            "https://ui-avatars.com/api/?name=" + response.getUsername(),
                            LocalDate.of(2000, 1, 1),
                            "unspecified",
                            "en",
                            country
                    )
            );

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/isValidUsername")
    public ResponseEntity<?> isValidUsername(@RequestParam String username) {
        try {
            Map<String, Object> response = profileService.validateUsername(username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("isValid", false);
            errorResponse.put("message", "Error validating username: " + e.getMessage());
            errorResponse.put("username", username);
            errorResponse.put("status", false);
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String resolveCountry(Profile user) {
        // map of calling codes (without +) -> ISO country codes (lowercase)
        Map<String, String> callingCodeToIso = Map.ofEntries(
                Map.entry("91", "in"),
                Map.entry("1", "us"),    // +1 is ambiguous (US/CA); default to us
                Map.entry("44", "gb"),
                Map.entry("61", "au"),
                Map.entry("49", "de"),
                Map.entry("86", "cn"),
                Map.entry("81", "jp"),
                Map.entry("33", "fr"),
                Map.entry("34", "es"),
                Map.entry("39", "it"),
                Map.entry("55", "br"),
                Map.entry("7", "ru"),
                Map.entry("27", "za"),
                Map.entry("65", "sg")
        );

        // try phone number parsing (E.164 like +911234567890)
        try {
            String phone = null;
            try {
                phone = user.getPhone();
            } catch (Exception e) {
                try {
                    phone = (String) Profile.class.getMethod("getPhone").invoke(user);
                } catch (NoSuchMethodException ignored) { }
            }
            if (phone != null && phone.startsWith("+")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\+(\\d{1,3})").matcher(phone);
                if (m.find()) {
                    String code = m.group(1);
                    return callingCodeToIso.getOrDefault(code, "unspecified");
                }
            }
        } catch (Exception ignored) { }

        return "unspecified";
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

    @PutMapping("/forgetPassword")
    public ResponseEntity<?> forgetPassword(@RequestBody ForgetPasswordRequest forgetPasswordRequest){
        Authentication authentication;
        Profile user =  profileService.changeUserPassword(forgetPasswordRequest.getPhoneNumber() , forgetPasswordRequest.getNewPassword());

        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), forgetPasswordRequest.getNewPassword()));
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
