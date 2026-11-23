package com.basic.JWTSecurity.artwork_server.geo;

import com.maxmind.db.Reader;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.Optional;

/**
 * IP -> ISO 3166-1 alpha-2 country lookup using the DB-IP Lite (CC BY 4.0)
 * MMDB bundled in resources/geo/dbip-country-lite.mmdb. No network calls,
 * no permission prompts: the app sends X-Forwarded-For set by Azure (or the
 * client falls back to its device locale country via X-Device-Country).
 */
@Slf4j
@Service
public class GeoService {

    private final Reader reader;

    public GeoService() {
        Reader r;
        try (InputStream in = new ClassPathResource("geo/dbip-country-lite.mmdb").getInputStream()) {
            r = new Reader(in);
            log.info("GeoService: DB-IP country database loaded");
        } catch (Exception e) {
            log.error("GeoService: failed to load geo database, country detection disabled", e);
            r = null;
        }
        this.reader = r;
    }

    /** ISO2 country code for a public IP, empty for private/invalid/unknown. */
    public Optional<String> countryForIp(String ip) {
        if (reader == null || ip == null || ip.isBlank()) return Optional.empty();
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            if (address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                return Optional.empty();
            }
            JsonNode result = reader.get(address, JsonNode.class);
            if (result == null) return Optional.empty();
            JsonNode iso = result.path("country").path("iso_code");
            if (iso.isTextual() && GeoCountries.isIso2(iso.asText())) {
                return Optional.of(iso.asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
    }
}
