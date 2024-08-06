package com.basic.JWTSecurity.controller;


import com.basic.JWTSecurity.model.ArtWork;
import com.basic.JWTSecurity.service.ArtworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
//@PreAuthorize("hasRole('USER')")
public class ArtWorkController {
    @Autowired
    private ArtworkService artworkService;
    @PostMapping("/save")
    public String save(@RequestBody ArtWork artWork){
        return artworkService.save(artWork);
    }
    @DeleteMapping("/delete")
    public void delete(@PathVariable String id){
        artworkService.delete(id);
    }
    @GetMapping("/artwork")
    public List<ArtWork> getArtwork()
    {
        return artworkService.getData();
    }

}
