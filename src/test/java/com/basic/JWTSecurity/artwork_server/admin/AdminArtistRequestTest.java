package com.basic.JWTSecurity.artwork_server.admin;

import com.basic.JWTSecurity.artwork_server.admin.AdminApi.AdminArtistRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminArtistRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void educationAndAwardsBindFromSnakeAndPlainKeys() throws Exception {
        // Regression: PUT /admin/artists/{id} used to silently drop education/awards
        // because the request record had no such fields (200 but nothing persisted).
        AdminArtistRequest request = mapper.readValue(
                "{\"education\": \"Gérôme\", \"awards\": \"Stripschapprijs (2004)\"}",
                AdminArtistRequest.class);
        assertEquals("Gérôme", request.education());
        assertEquals("Stripschapprijs (2004)", request.awards());
    }

    @Test
    void omittedFieldsStayNullForPartialUpdate() throws Exception {
        AdminArtistRequest request = mapper.readValue(
                "{\"nationality\": \"Italian\"}", AdminArtistRequest.class);
        assertEquals("Italian", request.nationality());
        assertNull(request.education());
        assertNull(request.awards());
        assertNull(request.description());
    }

    @Test
    void unknownKeysAreStillIgnored() throws Exception {
        AdminArtistRequest request = mapper.readValue(
                "{\"some_future_field\": 1, \"education\": \"X\"}", AdminArtistRequest.class);
        assertEquals("X", request.education());
    }
}
