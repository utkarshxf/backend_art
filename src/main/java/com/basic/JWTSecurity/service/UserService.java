package com.basic.JWTSecurity.service;

import com.basic.JWTSecurity.model.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserService {
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    public Profile registerUser(Profile user);

}

