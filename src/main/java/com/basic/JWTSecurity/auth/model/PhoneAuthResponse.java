package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// registered = false means the verified phone has no account yet; the app then asks for a username and password
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhoneAuthResponse {
    private boolean registered;
    private String phone;
    private String jwtToken;
    private String username;
    private List<String> roles;
}
