package com.basic.JWTSecurity.service;


import com.basic.JWTSecurity.model.ArtWork;

import java.util.List;

public interface ArtworkService {
    String save(ArtWork artWork);

    void delete(String id);

    List<ArtWork> getData();
}
