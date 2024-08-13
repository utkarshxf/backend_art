package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Year;

public interface YearService {

    Year create(Integer year);
    Year getById(Integer year);
}
