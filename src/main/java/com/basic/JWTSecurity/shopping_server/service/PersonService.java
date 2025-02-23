package com.basic.JWTSecurity.shopping_server.service;

import com.basic.JWTSecurity.shopping_server.collection.Person;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PersonService {
    String save(Person person);

    List<Person> getPersonStartWith(String name);

    void delete(String id);

    List<Person> getByPersionAge(Integer minAge, Integer maxAge);

    List<Document> getOldestpersonByCity();

    List<Document> getPopulationByCity();
}
