package com.basic.JWTSecurity.artwork_server.model.projection;

import java.time.LocalDate;
import java.util.List;

public interface UserProfileProjection {
    String getId();

    String getName();

    String getProfilePicture();

    Integer numberOfFollowing();
    Integer numberOfFollowers();
    List<ArtworkProjection> Artworks();

}

