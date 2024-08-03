package com.basic.JWTSecurity.repository;


import com.basic.JWTSecurity.model.ArtWork;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtworkRepository extends MongoRepository<ArtWork,String> {
//     List<ArtWork> findByNameWith(String name);
//     List <Person> findByAgeBetween(Integer min,Integer max);
}
