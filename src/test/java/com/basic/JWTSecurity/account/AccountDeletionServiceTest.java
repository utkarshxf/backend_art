package com.basic.JWTSecurity.account;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountDeletionServiceTest {

    private static final String BUCKET = "loyal-optics-388515.firebasestorage.app";

    @Test
    void storageObjectNameOnlyForDownloadUrlsOfOurBucket() {
        assertEquals(Optional.of("images/1f2e.jpg"), AccountDeletionService.storageObjectName(
                "https://firebasestorage.googleapis.com/v0/b/" + BUCKET + "/o/images%2F1f2e.jpg?alt=media&token=abc", BUCKET));
        assertEquals(Optional.of("chat/a__b/a/x y.jpg"), AccountDeletionService.storageObjectName(
                "https://firebasestorage.googleapis.com/v0/b/" + BUCKET + "/o/chat%2Fa__b%2Fa%2Fx%20y.jpg?alt=media", BUCKET));
        // other bucket, other host, not a URL
        assertEquals(Optional.empty(), AccountDeletionService.storageObjectName(
                "https://firebasestorage.googleapis.com/v0/b/other.appspot.com/o/images%2Fa.jpg?alt=media", BUCKET));
        assertEquals(Optional.empty(), AccountDeletionService.storageObjectName(
                "https://upload.wikimedia.org/wikipedia/commons/a/a8/CristoRaffaello.jpg", BUCKET));
        assertEquals(Optional.empty(), AccountDeletionService.storageObjectName("https://ui-avatars.com/api/?name=x", BUCKET));
        assertEquals(Optional.empty(), AccountDeletionService.storageObjectName("not a url", BUCKET));
        assertEquals(Optional.empty(), AccountDeletionService.storageObjectName(null, BUCKET));
    }
}
