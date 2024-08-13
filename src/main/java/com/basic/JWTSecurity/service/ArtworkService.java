package com.basic.JWTSecurity.service;


import com.basic.JWTSecurity.model.ArtWork;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ArtworkService {
    String save(ArtWork artWork);

    void delete(String id);

    List<ArtWork> getData();

    public Page<ArtWork> getArtworkWithPagination(int offset , int pageSize);
}
