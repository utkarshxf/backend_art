package com.basic.JWTSecurity.artwork_server.repository;


import com.basic.JWTSecurity.artwork_server.model.Year;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface YearRepository extends Neo4jRepository<Year,String> {

    Optional<Year>  findByYear(Integer year);
}
