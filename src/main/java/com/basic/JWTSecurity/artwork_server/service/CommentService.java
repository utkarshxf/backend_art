package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Comment;

public interface CommentService {

    Comment create(Comment comment, String userId, String artworkId);
    Comment update(String id,Comment comment );

    void deleteById(String id);
}
