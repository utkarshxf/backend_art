package com.basic.JWTSecurity.safety;

import com.basic.JWTSecurity.chat.service.ChatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blocking users, reporting content, and the developer's review of reports. */
@RestController
public class SafetyApi {

    private final SafetyService safety;

    public SafetyApi(SafetyService safety) {
        this.safety = safety;
    }

    public record ReportRequest(String targetType, String targetId, String targetOwnerId, String reason, String note) {
    }

    public record ResolveRequest(String action) {
    }

    @PostMapping("/blocks/{userId}")
    public Map<String, Object> block(@PathVariable String userId, Principal principal) {
        safety.block(caller(principal), userId);
        return ok();
    }

    @DeleteMapping("/blocks/{userId}")
    public Map<String, Object> unblock(@PathVariable String userId, Principal principal) {
        safety.unblock(caller(principal), userId);
        return ok();
    }

    @GetMapping("/blocks")
    public Map<String, Object> blocked(Principal principal) {
        Map<String, Object> body = ok();
        body.put("blocked", safety.blockedBy(caller(principal)));
        return body;
    }

    /** Users the caller may not see or contact, in either direction; the app hides their content. */
    @GetMapping("/blocks/hidden")
    public Map<String, Object> hidden(Principal principal) {
        Map<String, Object> body = ok();
        body.put("hidden", safety.hiddenUsers(caller(principal)));
        return body;
    }

    @PostMapping("/reports")
    public Map<String, Object> report(@RequestBody ReportRequest request, Principal principal) {
        safety.report(caller(principal), request.targetType(), request.targetId(), request.targetOwnerId(),
                request.reason(), request.note());
        return ok();
    }

    @GetMapping("/admin/reports")
    public List<ContentReport> reports(@RequestParam(required = false) String status) {
        return safety.openReports(status);
    }

    @PostMapping("/admin/reports/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable String id, @RequestBody ResolveRequest request) {
        safety.resolve(id, request.action());
        return ok();
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> error(ChatException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("status", false);
        return new ResponseEntity<>(body, e.getStatus());
    }

    private static String caller(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return principal.getName();
    }

    private static Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", true);
        return body;
    }
}
