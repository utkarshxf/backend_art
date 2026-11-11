package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.dto.AdminArtistUpsertRecord;
import com.basic.JWTSecurity.artwork_server.dto.AdminUserDetailsRecord;
import com.basic.JWTSecurity.artwork_server.dto.AdminUserSummaryRecord;
import com.basic.JWTSecurity.artwork_server.dto.ArtworkRecord;
import com.basic.JWTSecurity.artwork_server.dto.CatalogueStatsRecord;
import com.basic.JWTSecurity.artwork_server.dto.StatusUpdateRequest;
import com.basic.JWTSecurity.artwork_server.model.Status;
import com.basic.JWTSecurity.artwork_server.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Catalogue management surface for the admin panel. Everything here needs ROLE_ADMIN (granted to the usernames
// in the app.admin.usernames app setting); ordinary app accounts keep their existing access unchanged.
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class AdminApi {

    private final AdminService adminService;

    @PostMapping("/artists")
    public ResponseEntity<Map<String, String>> createArtist(@RequestBody AdminArtistUpsertRecord record) {
        String id = adminService.createArtist(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/artists/{artistId}")
    public ResponseEntity<Void> updateArtist(@PathVariable String artistId,
                                             @RequestBody AdminArtistUpsertRecord record) {
        adminService.updateArtist(artistId, record);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/artists/{artistId}/artworks")
    public ResponseEntity<Map<String, String>> createArtwork(@PathVariable String artistId,
                                                             @RequestBody ArtworkRecord record) {
        String id = adminService.createArtwork(artistId, record);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/artworks/{artworkId}")
    public ResponseEntity<Void> updateArtwork(@PathVariable String artworkId,
                                              @RequestBody ArtworkRecord record) {
        adminService.updateArtwork(artworkId, record);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/artworks/{artworkId}")
    public ResponseEntity<Map<String, Object>> getArtworkForAudit(@PathVariable String artworkId) {
        return ResponseEntity.ok(adminService.getArtworkForAudit(artworkId));
    }

    @PutMapping("/artworks/{artworkId}/status")
    public ResponseEntity<Void> updateArtworkStatus(@PathVariable String artworkId,
                                                    @RequestBody StatusUpdateRequest request) {
        adminService.updateArtworkStatus(artworkId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    // Soft delete: the artwork leaves every feed (status DELETED is filtered everywhere) but stays recoverable
    @DeleteMapping("/artworks/{artworkId}")
    public ResponseEntity<Void> deleteArtwork(@PathVariable String artworkId) {
        adminService.updateArtworkStatus(artworkId, Status.DELETED);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<CatalogueStatsRecord> getCatalogueStats() {
        return ResponseEntity.ok(adminService.getCatalogueStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserSummaryRecord>> listUsers(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "sort", defaultValue = "joined") String sort,
            @RequestParam(value = "order", defaultValue = "desc") String order,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(adminService.listUsers(query, sort, order, limit));
    }

    @GetMapping("/users/{username}")
    public ResponseEntity<AdminUserDetailsRecord> getUserDetails(@PathVariable String username) {
        return ResponseEntity.ok(adminService.getUserDetails(username));
    }

    @PostMapping("/genres")
    public ResponseEntity<Map<String, String>> createGenre(@RequestBody Map<String, String> body) {
        Map<String, String> genre = adminService.createGenre(body.get("name"));
        return ResponseEntity.status(HttpStatus.CREATED).body(genre);
    }
}
