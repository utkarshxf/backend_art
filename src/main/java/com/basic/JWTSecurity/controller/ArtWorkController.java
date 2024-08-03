package com.basic.JWTSecurity.controller;


import com.basic.JWTSecurity.model.ArtWork;
import com.basic.JWTSecurity.service.ArtworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('ROLE_USER')")
@RequestMapping("/artwork")
public class ArtWorkController {
    @Autowired
    private ArtworkService artworkService;
    @PostMapping
    public String save(@RequestBody ArtWork artWork){
        return artworkService.save(artWork);
    }
//    @GetMapping
//    public List<ArtWork> getArtworkStartWith(@RequestParam("name") String name){
//        return artworkService.getArtworkStartWith(name);
//    }
    @DeleteMapping
    public void delete(@PathVariable String id){
        artworkService.delete(id);
    }

}
