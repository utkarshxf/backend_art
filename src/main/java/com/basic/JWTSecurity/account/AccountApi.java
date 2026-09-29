package com.basic.JWTSecurity.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/** DELETE /account: permanently deletes the caller's own account (the JWT subject); see AccountDeletionService. */
@RestController
@RequestMapping("/account")
public class AccountApi {

    private final AccountDeletionService deletionService;

    public AccountApi(AccountDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteMyAccount(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return body(HttpStatus.UNAUTHORIZED, "Please log in again", false);
        }
        try {
            deletionService.deleteAccount(principal.getName());
            return body(HttpStatus.OK, "Your account was deleted", true);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(AccountApi.class).error("Deleting account {} failed", principal.getName(), e);
            return body(HttpStatus.INTERNAL_SERVER_ERROR, "We couldn't delete your account. Please try again.", false);
        }
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, boolean deleted) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", deleted);
        body.put("deleted", deleted);
        return new ResponseEntity<>(body, status);
    }
}
