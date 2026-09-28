package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhoneSignupRequest {
    private String firebaseIdToken;
    private String username;
    private String password;
}
