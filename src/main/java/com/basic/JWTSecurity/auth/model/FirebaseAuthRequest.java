package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FirebaseAuthRequest {
    // Firebase ID token the app gets after phone (SMS code) or Google sign-in
    private String firebaseIdToken;
}
