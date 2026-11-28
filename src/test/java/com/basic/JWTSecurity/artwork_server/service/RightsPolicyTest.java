package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.model.rights.RightsStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RightsPolicyTest {

    @Test
    void publicDomainVariantsAllowSaveAndWallpaper() {
        for (String raw : new String[]{
                "Public domain",
                "Public Domain (PD-US)",
                "This work is in the publicdomain in its country of origin",
                "No known copyright restrictions"
        }) {
            RightsStatus status = RightsPolicy.normalize(raw, null);
            assertEquals(RightsStatus.PUBLIC_DOMAIN, status, raw);
            assertTrue(RightsPolicy.canSave(status), raw);
            assertTrue(RightsPolicy.canSetWallpaper(status), raw);
        }
    }

    @Test
    void cc0AllowsSaveAndWallpaper() {
        RightsStatus status = RightsPolicy.normalize("CC0 1.0 Universal", "https://commons.wikimedia.org/x");
        assertEquals(RightsStatus.CC0, status);
        assertTrue(RightsPolicy.canSave(status));
        assertTrue(RightsPolicy.canSetWallpaper(status));
    }

    @Test
    void nonCommercialFailsClosed() {
        RightsStatus status = RightsPolicy.normalize("Open Access (non-commercial use only)", null);
        assertEquals(RightsStatus.NON_COMMERCIAL, status);
        assertFalse(RightsPolicy.canSave(status));
        assertFalse(RightsPolicy.canSetWallpaper(status));
    }

    @Test
    void shareAlikeAndAttributionFailClosed() {
        RightsStatus sa = RightsPolicy.normalize("CC BY-SA 4.0", null);
        assertEquals(RightsStatus.CC_BY_SA, sa);
        assertFalse(RightsPolicy.canSave(sa));

        RightsStatus by = RightsPolicy.normalize("Creative Commons Attribution 4.0", null);
        assertEquals(RightsStatus.CC_BY, by);
        assertFalse(RightsPolicy.canSave(by));
    }

    @Test
    void missingOrUnknownLicenceFailsClosed() {
        assertEquals(RightsStatus.UNKNOWN, RightsPolicy.normalize(null, null));
        assertEquals(RightsStatus.UNKNOWN, RightsPolicy.normalize("", ""));
        RightsStatus weird = RightsPolicy.normalize("All rights reserved", null);
        assertEquals(RightsStatus.UNKNOWN, weird);
        assertFalse(RightsPolicy.canSave(weird));
        assertFalse(RightsPolicy.canSetWallpaper(weird));
    }
}
