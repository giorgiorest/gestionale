package it.gestionale.api.rapportini;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialeRapportinoService {

    private final MaterialeRapportinoRepository materials;
    private final RapportinoRepository dailyReports;
    private final Clock clock;

    public MaterialeRapportinoService(MaterialeRapportinoRepository materials, RapportinoRepository dailyReports,
                                      Clock clock) {
        this.materials = materials;
        this.dailyReports = dailyReports;
        this.clock = clock;
    }

    /** Righe di materiale del rapportino, nell'ordine di inserimento: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<MaterialeRapportinoDto> listByDailyReport(UUID dailyReportId) {
        return materials
                .findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(dailyReportId,
                        ContestoTenant.require())
                .stream().map(MaterialeRapportinoDto::from).toList();
    }

    /**
     * Crea o aggiorna la riga con l'id indicato (generato dal client). Idempotente: reinviare la
     * stessa operazione dalla outbox produce lo stesso risultato. Una riga cancellata logicamente
     * torna attiva (vedi {@link MaterialeRapportino#update}).
     */
    @Transactional
    public MaterialeRapportinoDto upsert(UUID id, MaterialeRapportinoDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        UUID dailyReportId = input.dailyReportId();
        Optional<MaterialeRapportino> existing = materials.findByIdAndTenantId(id, tenantId);

        boolean dailyReportChanged = existing.map(m -> !m.getDailyReportId().equals(dailyReportId)).orElse(true);
        Rapportino report = requireValidDailyReport(dailyReportId, tenantId, dailyReportChanged);
        // Un rapportino non più DRAFT non si può modificare nemmeno nelle sue righe (#51), anche
        // quando il riferimento al rapportino non cambia.
        report.requireDraft();

        MaterialeRapportino material = existing
                .map(m -> {
                    m.update(dailyReportId, input.description(), input.quantity(), input.unit(), input.notes(),
                            actorUserId, clock.instant());
                    return m;
                })
                .orElseGet(() -> materials.save(new MaterialeRapportino(id, tenantId, dailyReportId,
                        input.description(), input.quantity(), input.unit(), input.notes(), actorUserId)));
        materials.flush();
        return MaterialeRapportinoDto.from(material);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     * Una riga di un rapportino non più DRAFT non si può cancellare da qui (#51); una riga già
     * cancellata non fa il controllo, per non rompere il re-invio idempotente dalla outbox.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        materials.findByIdAndTenantId(id, tenantId).ifPresent(material -> {
            if (!material.isDeleted()) {
                dailyReports.findByIdAndTenantId(material.getDailyReportId(), tenantId)
                        .ifPresent(Rapportino::requireDraft);
            }
            material.markDeleted(actorUserId, clock.instant());
        });
        materials.flush();
    }

    /** Il rapportino deve essere un'anagrafica attiva dello stesso tenant, sempre restituita per il suo id. */
    private Rapportino requireValidDailyReport(UUID dailyReportId, UUID tenantId, boolean mustBeActive) {
        Optional<Rapportino> found = dailyReports.findByIdAndTenantId(dailyReportId, tenantId);
        if (mustBeActive && found.map(Rapportino::isDeleted).orElse(true)) {
            throw new RiferimentoMaterialeSconosciutoException("Rapportino non valido: " + dailyReportId);
        }
        return found.orElseThrow(
                () -> new RiferimentoMaterialeSconosciutoException("Rapportino non valido: " + dailyReportId));
    }
}
