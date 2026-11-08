package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.dto.AdminArtistUpsertRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.model.Status;

import java.util.Map;

// Catalogue management for the admin panel. All writes are ROLE_ADMIN only (enforced in AdminApi) and are
// deliberately separate from the public self-service API: no User personas are created for artists, unknown
// years stay unknown (no current-year default, no fabricated releasedDate), and genres must already exist.
public interface AdminService {

    /** Create a catalogue artist. Returns the artist id (generated from the name when not supplied). */
    String createArtist(AdminArtistUpsertRecord record);

    /** Merge-update an artist: null fields keep their current values. */
    void updateArtist(String artistId, AdminArtistUpsertRecord record);

    /** Create an artwork under any artist. genreId is required and must exist; releaseYear may be null. */
    String createArtwork(String artistId, ArtworkRecord record);

    /** Merge-update an artwork: null fields keep their current values. */
    void updateArtwork(String artworkId, ArtworkRecord record);

    void updateArtworkStatus(String artworkId, Status status);

    /** Raw scalar fields of one artwork for audit display (no relationship graph). */
    Map<String, Object> getArtworkForAudit(String artworkId);

    /** Create a genre (id derived from the name like the importer did) or return the existing one. */
    Map<String, String> createGenre(String name);
}
