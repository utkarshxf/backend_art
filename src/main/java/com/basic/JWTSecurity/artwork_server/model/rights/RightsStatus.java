package com.basic.JWTSecurity.artwork_server.model.rights;

/**
 * Normalized rights status for an artwork image, derived from the raw
 * license_info / source_url stored on the artwork. Clients must never parse
 * license strings themselves; the server owns this mapping.
 */
public enum RightsStatus {
    PUBLIC_DOMAIN,
    CC0,
    CC_BY,
    CC_BY_SA,
    NON_COMMERCIAL,
    UNKNOWN
}
