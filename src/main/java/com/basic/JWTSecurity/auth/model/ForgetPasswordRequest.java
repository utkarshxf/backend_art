package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForgetPasswordRequest {
    private String phoneNumber;
    private String newPassword;
}
    