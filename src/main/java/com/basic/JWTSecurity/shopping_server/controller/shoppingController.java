package com.basic.JWTSecurity.shopping_server.controller;

import com.basic.JWTSecurity.shopping_server.collection.Person;
import com.basic.JWTSecurity.shopping_server.service.PersonService;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/shopping")
public class shoppingController {
    @Autowired
    private PersonService personService;
    @PostMapping
    public String save(@RequestBody Person person){
        return personService.save(person);
    }
    @GetMapping
    public List<Person> getPersonStartWith(@RequestParam("name") String name){
        return personService.getPersonStartWith(name);
    }
    @DeleteMapping
    public void delete(@PathVariable String id){
        personService.delete(id);
    }
    @GetMapping("/age")
    public List<Person> getByPersonAge(@RequestParam Integer minAge,
                                       @RequestParam Integer maxAge){
        return personService.getByPersionAge(minAge,maxAge);
    }
    @GetMapping("/oldestPerson")
    public List<Document> getOldestPerson(){
        return personService.getOldestpersonByCity();
    }
    @GetMapping("/populationByCity")
    public List<Document>  getPopulationByCity(){
        return personService.getPopulationByCity();
    }
}
