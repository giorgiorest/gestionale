package it.gestionale.api.costimanodopera;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI costo manodopera di una commessa (#53). Permesso dedicato {@code labor-cost:read}, non
 * {@code work-entries:read} come {@code hoursreport}: a differenza delle ore, il costo è un dato
 * sensibile (retribuzione implicita) e non tutti i ruoli che leggono le ore devono leggerlo
 * (vedi ADR 0021 e {@code Ruolo}).
 */
@RestController
public class CostiManodoperaController {

    private final CostiManodoperaService service;

    public CostiManodoperaController(CostiManodoperaService service) {
        this.service = service;
    }

    @GetMapping("/api/commesse/{commessaId}/reports/labor-cost")
    @PreAuthorize("hasAuthority('labor-cost:read')")
    public CostiManodoperaDto report(@PathVariable UUID commessaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(commessaId, from, to);
    }
}
