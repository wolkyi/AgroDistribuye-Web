package com.agrodistribuye.api;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "application", "AgroDistribuye",
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString(),
                "dataMode", "SIMULATED"
        );
    }
}
