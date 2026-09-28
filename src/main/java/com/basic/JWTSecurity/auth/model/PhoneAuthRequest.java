package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhoneAuthRequest {
    // Firebase ID token the app gets after the user entered the SMS code
    private String firebaseIdToken;
}
