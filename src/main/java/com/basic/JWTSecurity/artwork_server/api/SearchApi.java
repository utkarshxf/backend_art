package com.basic.JWTSecurity.artwork_server.api;

import com.basic.JWTSecurity.artwork_server.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Search screen: GET /search?q=starry night&type=all|artworks|artists|people&skip=0&limit=20.
 * "all" returns the first page of artworks plus a few artists and people (the "Top" tab); the other types page one
 * list with skip/limit.
 */
@RestController
@RequestMapping("/search")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class SearchApi {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_SKIP = 1000;

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam("q") String query,
                                    @RequestParam(defaultValue = "all") String type,
                                    @RequestParam(defaultValue = "0") int skip,
                                    @RequestParam(defaultValue = "20") int limit) {
        SearchService.Type searchType;
        try {
            searchType = SearchService.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return error("type must be one of all, artworks, artists, people");
        }
        String q = query.strip();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        int page = Math.max(1, Math.min(limit, MAX_LIMIT));
        int offset = Math.max(0, Math.min(skip, MAX_SKIP));
        return ResponseEntity.ok(searchService.search(q, searchType, offset, page));
    }

    private static ResponseEntity<Map<String, Object>> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", false);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}
