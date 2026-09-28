package com.basic.JWTSecurity.auth.api;


import com.basic.JWTSecurity.artwork_server.dto.UserRegistrationRequestRecord;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import com.basic.JWTSecurity.auth.model.*;
import com.basic.JWTSecurity.auth.security.FirebaseTokenVerifier;
import com.basic.JWTSecurity.auth.service.ProfileService;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Autowired
    private FirebaseTokenVerifier firebaseTokenVerifier;

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

    // Login step 1: the app signed in with Firebase (SMS code or Google). Log in if an account uses the verified
    // phone number / email, otherwise answer registered = false so the app asks for a username and password.
    @PostMapping({"/auth/firebase", "/auth/phone"})
    public ResponseEntity<?> firebaseAuth(@RequestBody FirebaseAuthRequest request) {
        FirebaseTokenVerifier.VerifiedIdentity identity;
        try {
            identity = firebaseTokenVerifier.verify(request.getFirebaseIdToken());
        } catch (FirebaseTokenVerifier.InvalidTokenException e) {
            return error(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
        Optional<Profile> profile = findAccount(identity);
        if (profile.isEmpty()) {
            return ResponseEntity.ok(new FirebaseAuthResponse(false, identity.phone(), identity.email(), null, null, List.of()));
        }
        JwtResponse token = tokenFor(profile.get().getUsername());
        return ResponseEntity.ok(new FirebaseAuthResponse(true, identity.phone(), identity.email(),
                token.getJwtToken(), token.getUsername(), token.getRoles()));
    }

    // Login step 2 for a new phone number / Google account: create the account and log in
    @PostMapping({"/auth/firebase/signup", "/auth/phone/signup"})
    public ResponseEntity<?> firebaseSignup(@RequestBody FirebaseSignupRequest request) {
        FirebaseTokenVerifier.VerifiedIdentity identity;
        try {
            identity = firebaseTokenVerifier.verify(request.getFirebaseIdToken());
        } catch (FirebaseTokenVerifier.InvalidTokenException e) {
            return error(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
        if (findAccount(identity).isPresent()) {
            return error("An account already uses this " + (identity.phone() != null ? "phone number" : "email"), HttpStatus.BAD_REQUEST);
        }
        String username = request.getUsername() == null ? "" : request.getUsername().trim().toLowerCase();
        Map<String, Object> usernameCheck = profileService.validateUsername(username);
        if (!Boolean.TRUE.equals(usernameCheck.get("isValid"))) {
            return error(String.valueOf(usernameCheck.getOrDefault("message", "Username is not available")), HttpStatus.BAD_REQUEST);
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return error("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        }
        Profile profile = new Profile();
        profile.setUsername(username);
        profile.setPhone(identity.phone());
        profile.setEmail(identity.email());
        profile.setPassword(request.getPassword());
        return register(profile);
    }

    // Phone number first (SMS sign-in), otherwise the verified Google email
    private Optional<Profile> findAccount(FirebaseTokenVerifier.VerifiedIdentity identity) {
        if (identity.phone() != null) {
            Optional<Profile> byPhone = profileService.findByPhone(identity.phone());
            if (byPhone.isPresent()) return byPhone;
        }
        return identity.email() != null ? profileService.findByEmail(identity.email()) : Optional.empty();
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody Profile user,
                                          @RequestHeader(value = "X-Firebase-Id-Token", required = false) String firebaseIdToken) {
        // The phone number must be proven with an SMS code; the one in the body is not trusted
        try {
            user.setPhone(firebaseTokenVerifier.verifiedPhone(firebaseIdToken));
        } catch (FirebaseTokenVerifier.InvalidTokenException e) {
            return error(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
        return register(user);
    }

    private ResponseEntity<?> register(Profile user) {
        try {
            Profile registeredUser = profileService.registerUser(user);
            if (registeredUser == null) {
                return error("User registration failed", HttpStatus.BAD_REQUEST);
            }

            JwtResponse response = tokenFor(registeredUser.getUsername());

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
            return error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private JwtResponse tokenFor(String username) {
        UserDetails userDetails = profileService.loadUserByUsername(username);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return new JwtResponse(jwtUtils.generateTokenFromUsername(userDetails), userDetails.getUsername(), roles);
    }

    private static ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("status", false);
        return new ResponseEntity<>(map, status);
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
        // Only the owner of the phone number (proven with an SMS code) may reset the password
        String phone;
        try {
            phone = firebaseTokenVerifier.verifiedPhone(forgetPasswordRequest.getFirebaseIdToken());
        } catch (FirebaseTokenVerifier.InvalidTokenException e) {
            return error(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }

        Authentication authentication;
        Profile user;
        try {
            user = profileService.changeUserPassword(phone, forgetPasswordRequest.getNewPassword());
        } catch (UsernameNotFoundException e) {
            return error("No account uses this phone number", HttpStatus.NOT_FOUND);
        }

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
