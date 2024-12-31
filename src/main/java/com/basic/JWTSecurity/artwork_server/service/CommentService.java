package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetComments;

import java.util.List;

public interface CommentService {

    Comment create(Comment comment, String userId, String artworkId);
    Comment update(String id,Comment comment );

    void deleteById(String id);

    List<GetComments> getArtworkComments(String artworkId);
}
