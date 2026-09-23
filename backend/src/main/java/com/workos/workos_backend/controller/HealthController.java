package com.workos.workos_backend.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Foundation health check, not a business endpoint. Only reports that the
 * API process is up — it does not check database connectivity, so it must
 * not be read as a database health signal.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "workos-backend");
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
