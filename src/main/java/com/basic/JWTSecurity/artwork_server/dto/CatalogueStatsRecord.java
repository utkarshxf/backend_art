package com.basic.JWTSecurity.artwork_server.dto;

import java.util.List;

// Catalogue totals for the admin panel overview. totalArtworks and the per-artist counts exclude
// soft-deleted artworks (status DELETED never leaves the database but leaves every feed);
// deletedArtworks reports those separately so the numbers reconcile.
public record CatalogueStatsRecord(
        long totalArtworks,
        long totalArtists,
        long totalGenres,
        long deletedArtworks,
        List<ArtistArtworkCount> artists) {

    public record ArtistArtworkCount(String id, String name, long artworkCount) {
    }
}
