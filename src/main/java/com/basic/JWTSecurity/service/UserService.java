package com.basic.JWTSecurity.service;

import com.basic.JWTSecurity.model.Profile;
import com.basic.JWTSecurity.model.TokenRequest;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserService {
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    public Profile registerUser(Profile user);

    public Claims checkToken(TokenRequest token);
}

