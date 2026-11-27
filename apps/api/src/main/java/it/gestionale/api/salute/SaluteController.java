package it.gestionale.api.salute;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint di liveness pubblico, usato da load balancer e compose.
 */
@RestController
@RequestMapping("/api/health")
public class SaluteController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
