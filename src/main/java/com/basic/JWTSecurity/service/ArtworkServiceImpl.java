package com.basic.JWTSecurity.service;

import com.basic.JWTSecurity.model.ArtWork;
import com.basic.JWTSecurity.repository.ArtworkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArtworkServiceImpl implements ArtworkService {
    @Autowired
    private ArtworkRepository artworkRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Override
    public String save(ArtWork artWork) {
        return artworkRepository.save(artWork).getArtworkId();
    }

    @Override
    public void delete(String id) {
        artworkRepository.deleteById(id);
    }

    @Override
    public List<ArtWork> getData() {
        return artworkRepository.findAll();
    }


}

