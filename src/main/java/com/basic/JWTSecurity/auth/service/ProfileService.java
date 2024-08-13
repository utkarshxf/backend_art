package com.basic.JWTSecurity.auth.service;

import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.model.TokenRequest;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface ProfileService {
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    public Profile registerUser(Profile user);

    public Claims checkToken(TokenRequest token);
}

