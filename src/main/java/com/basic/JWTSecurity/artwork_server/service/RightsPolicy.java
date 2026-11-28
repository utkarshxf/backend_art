package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.model.rights.RightsStatus;

import java.util.Locale;

/**
 * Maps raw licence text (and the source it came from) to a normalized
 * {@link RightsStatus}, and derives per-artwork capabilities from it.
 *
 * Approved scope is public-domain works only: save/wallpaper are allowed for
 * PUBLIC_DOMAIN and CC0, everything else fails closed.
 *
 * Raw licence strings are messy ("Public Domain", "PD-US", "CC0",
 * "No known copyright restrictions", "Open Access (non-commercial)"), so this
 * is the single place that interprets them.
 */
public final class RightsPolicy {

    private RightsPolicy() {
    }

    public static RightsStatus normalize(String licenseInfo, String sourceUrl) {
        String text = ((licenseInfo == null ? "" : licenseInfo) + " " + (sourceUrl == null ? "" : sourceUrl))
                .toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return RightsStatus.UNKNOWN;
        }
        if (text.contains("public domain") || text.contains("publicdomain")
                || text.contains("pd-us") || text.contains("pdm")
                || text.contains("no known copyright")) {
            return RightsStatus.PUBLIC_DOMAIN;
        }
        if (text.contains("cc0") || text.contains("cc-0") || text.contains("creative commons zero")) {
            return RightsStatus.CC0;
        }
        if (text.contains("by-nc") || text.contains("non-commercial") || text.contains("noncommercial")) {
            return RightsStatus.NON_COMMERCIAL;
        }
        if (text.contains("by-sa") || text.contains("share-alike") || text.contains("sharealike")) {
            return RightsStatus.CC_BY_SA;
        }
        if (text.contains("cc by") || text.contains("cc-by") || text.contains("creative commons attribution")) {
            return RightsStatus.CC_BY;
        }
        return RightsStatus.UNKNOWN;
    }

    /** Saving the high-res image to the device gallery. Public-domain/CC0 only. */
    public static boolean canSave(RightsStatus status) {
        return status == RightsStatus.PUBLIC_DOMAIN || status == RightsStatus.CC0;
    }

    /** Setting the artwork as device wallpaper. Public-domain/CC0 only. */
    public static boolean canSetWallpaper(RightsStatus status) {
        return status == RightsStatus.PUBLIC_DOMAIN || status == RightsStatus.CC0;
    }
}
