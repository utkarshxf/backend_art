package com.basic.JWTSecurity.artwork_server.dto;

// One row of the admin users list. joinedAt comes from the Mongo ObjectId timestamp of the account
// record (null for accounts whose id is not an ObjectId). Counts come from the graph and are zero
// for accounts whose graph user is missing.
public record AdminUserSummaryRecord(
        String id,
        String username,
        String email,
        String phone,
        String joinedAt,
        boolean admin,
        boolean artist,
        long artworkCount,
        long likeCount,
        long followerCount,
        long commentCount) {
}
