package it.gestionale.api.licenze;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entitlements")
public class AbilitazioniController {

    private final AbilitazioniService entitlementService;

    public AbilitazioniController(AbilitazioniService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping
    public Abilitazioni current() {
        return entitlementService.current();
    }
}
