package com.basic.JWTSecurity.artwork_server.dto;

import com.basic.JWTSecurity.artwork_server.model.Status;

import java.util.List;

// Request bodies for the temporary bulk endpoints used by the one-off catalogue cleanup.
public final class CatalogueCleanupRequests {
    private CatalogueCleanupRequests() {}

    public record BulkStatusRequest(List<String> artworkIds, Status status) {}

    public record ArtworkImageUpdate(String id, String imageUrl, String imageUrlCompressed) {}
}
