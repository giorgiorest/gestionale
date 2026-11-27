package it.gestionale.api.riepilogoore;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cruscotto ore di una commessa (#52). Permesso riusato da {@code work-entries:read}: non ne
 * introduce uno nuovo, perché nel progetto chi legge le righe di ore legge sempre anche i
 * rapportini (stesso insieme di ruoli su entrambi, vedi {@code Ruolo}) — vale anche per
 * {@code caposquadra} (lettura piena, non ancora limitata alla squadra: scelta rimandata, come
 * per {@code RigaOreController}) e per l'assenza di permesso di {@code operaio}.
 */
@RestController
public class RiepilogoOreController {

    private final RiepilogoOreService service;

    public RiepilogoOreController(RiepilogoOreService service) {
        this.service = service;
    }

    @GetMapping("/api/commesse/{commessaId}/reports/hours")
    @PreAuthorize("hasAuthority('work-entries:read')")
    public RiepilogoOreDto report(@PathVariable UUID commessaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(commessaId, from, to);
    }
}
