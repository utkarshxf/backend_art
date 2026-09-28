package com.basic.JWTSecurity.auth.security;

import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import com.basic.JWTSecurity.artwork_server.repository.CommentRepository;
import com.basic.JWTSecurity.artwork_server.repository.FavoritesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ownership checks for write endpoints addressed by an object id rather than a user id, used from @PreAuthorize as
 * {@code @owner.ofFavorites(#id, authentication.name)}. The JWT subject is the username, which is also the Neo4j
 * User (and Artist) id.
 */
@Component("owner")
@RequiredArgsConstructor
public class Ownership {

    private final FavoritesRepository favoritesRepository;
    private final CommentRepository commentRepository;
    private final ArtworkRepository artworkRepository;

    public boolean ofFavorites(String favoritesId, String username) {
        return favoritesRepository.isCreatedBy(favoritesId, username);
    }

    public boolean ofComment(String commentId, String username) {
        return commentRepository.isPostedBy(commentId, username);
    }

    public boolean ofArtwork(String artworkId, String username) {
        return artworkRepository.isCreatedBy(artworkId, username);
    }
}
