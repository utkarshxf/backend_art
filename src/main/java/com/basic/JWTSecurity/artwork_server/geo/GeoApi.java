package com.basic.JWTSecurity.artwork_server.geo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public geo endpoint (v2.8.4). Called by the app at launch; result is cached
 * client-side and refreshed on each launch. No auth, no permission prompt:
 * country comes from the request IP (X-Forwarded-For as set by Azure Front
 * Door / App Service), falling back to the device locale country the client
 * sends in X-Device-Country.
 */
@RestController
@RequestMapping("/geo")
@CrossOrigin(value = "*")
@RequiredArgsConstructor
public class GeoApi {

    private final GeoService geoService;

    @GetMapping("/country")
    public ResponseEntity<Map<String, Object>> country(
            HttpServletRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "X-Device-Country", required = false) String deviceCountry) {

        Map<String, Object> body = new HashMap<>();
        String ip = clientIp(request, forwardedFor);
        Optional<String> fromIp = geoService.countryForIp(ip);
        if (fromIp.isPresent()) {
            body.put("country", fromIp.get());
            body.put("source", "ip");
            return ResponseEntity.ok(body);
        }
        if (GeoCountries.isIso2(deviceCountry)) {
            body.put("country", deviceCountry);
            body.put("source", "device");
            return ResponseEntity.ok(body);
        }
        body.put("country", null);
        body.put("source", "none");
        return ResponseEntity.ok(body);
    }

    private String clientIp(HttpServletRequest request, String forwardedFor) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
