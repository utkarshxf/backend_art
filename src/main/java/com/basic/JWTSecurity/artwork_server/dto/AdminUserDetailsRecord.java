package com.basic.JWTSecurity.artwork_server.dto;

import java.util.List;

// Full admin view of one account: auth profile (Mongo), graph profile (Neo4j) and activity counts.
public record AdminUserDetailsRecord(
        String id,
        String username,
        String email,
        String phone,
        String joinedAt,
        boolean admin,
        String name,
        String profilePicture,
        String dob,
        String gender,
        String language,
        String countryIso2,
        boolean artist,
        String artistId,
        String artistName,
        long artworkCount,
        long likeCount,
        long followerCount,
        long commentCount,
        List<AdminUserArtworkRecord> artworks,
        List<AdminUserLikeRecord> recentLikes) {

    public record AdminUserArtworkRecord(
            String id,
            String title,
            String status,
            String releasedDate,
            String imageUrlCompressed,
            long likeCount) {
    }

    public record AdminUserLikeRecord(
            String id,
            String title,
            String artistId,
            String likedAt) {
    }
}
