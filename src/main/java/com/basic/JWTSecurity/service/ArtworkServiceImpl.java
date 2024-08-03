package com.basic.JWTSecurity.service;

import com.basic.JWTSecurity.model.ArtWork;
import com.basic.JWTSecurity.repository.ArtworkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

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

//    @Override
//    public List<ArtWork> getArtworkStartWith(String name) {
////        return artworkRepository.findByNameWith(name);
//    }

    @Override
    public void delete(String id) {
        artworkRepository.deleteById(id);
    }





}

