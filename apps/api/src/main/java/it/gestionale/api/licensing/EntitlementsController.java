package it.gestionale.api.licensing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entitlements")
public class EntitlementsController {

    private final EntitlementService entitlementService;

    public EntitlementsController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping
    public Entitlements current() {
        return entitlementService.current();
    }
}
