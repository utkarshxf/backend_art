package com.basic.JWTSecurity.artwork_server.service;


import com.basic.JWTSecurity.artwork_server.model.Comment;
import com.basic.JWTSecurity.artwork_server.model.get_models.GetComments;
import com.basic.JWTSecurity.artwork_server.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements  CommentService{

    private final CommentRepository commentRepository;
    @Override
    public Comment create(Comment comment, String userId, String artworkId) {

        Comment comment1 = Comment.builder()
                .text(comment.getText())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .edited(false).build();

        Comment saved = commentRepository.save(comment1);
        commentRepository.addCommentRelationship(saved.getId(), artworkId,userId);


        return saved;
    }

    @Override
    public Comment update(String id, Comment comment) {


        return null;
    }

    @Override
    public void deleteById(String id) {
        commentRepository.deleteById(id);
    }

    @Override
    public List<GetComments> getArtworkComments(String artworkId) {
        return commentRepository.getArtworkComments(artworkId);
    }
}
