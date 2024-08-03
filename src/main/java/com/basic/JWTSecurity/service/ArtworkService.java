package com.basic.JWTSecurity.service;


import com.basic.JWTSecurity.model.ArtWork;

public interface ArtworkService {
    String save(ArtWork artWork);

//    List<ArtWork> getArtworkStartWith(String name);

    void delete(String id);
}
