package it.gestionale.api.dailyreportmaterials;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.dailyreports.DailyReport;
import it.gestionale.api.dailyreports.DailyReportRepository;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReportMaterialService {

    private final DailyReportMaterialRepository materials;
    private final DailyReportRepository dailyReports;
    private final Clock clock;

    public DailyReportMaterialService(DailyReportMaterialRepository materials, DailyReportRepository dailyReports,
                                      Clock clock) {
        this.materials = materials;
        this.dailyReports = dailyReports;
        this.clock = clock;
    }

    /** Righe di materiale del rapportino, nell'ordine di inserimento: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<DailyReportMaterialDto> listByDailyReport(UUID dailyReportId) {
        return materials
                .findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(dailyReportId,
                        TenantContext.require())
                .stream().map(DailyReportMaterialDto::from).toList();
    }

    /**
     * Crea o aggiorna la riga con l'id indicato (generato dal client). Idempotente: reinviare la
     * stessa operazione dalla outbox produce lo stesso risultato. Una riga cancellata logicamente
     * torna attiva (vedi {@link DailyReportMaterial#update}).
     */
    @Transactional
    public DailyReportMaterialDto upsert(UUID id, DailyReportMaterialDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID dailyReportId = input.dailyReportId();
        Optional<DailyReportMaterial> existing = materials.findByIdAndTenantId(id, tenantId);

        boolean dailyReportChanged = existing.map(m -> !m.getDailyReportId().equals(dailyReportId)).orElse(true);
        DailyReport report = requireValidDailyReport(dailyReportId, tenantId, dailyReportChanged);
        // Un rapportino non più DRAFT non si può modificare nemmeno nelle sue righe (#51), anche
        // quando il riferimento al rapportino non cambia.
        report.requireDraft();

        DailyReportMaterial material = existing
                .map(m -> {
                    m.update(dailyReportId, input.description(), input.quantity(), input.unit(), input.notes(),
                            actorUserId, clock.instant());
                    return m;
                })
                .orElseGet(() -> materials.save(new DailyReportMaterial(id, tenantId, dailyReportId,
                        input.description(), input.quantity(), input.unit(), input.notes(), actorUserId)));
        materials.flush();
        return DailyReportMaterialDto.from(material);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     * Una riga di un rapportino non più DRAFT non si può cancellare da qui (#51); una riga già
     * cancellata non fa il controllo, per non rompere il re-invio idempotente dalla outbox.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        UUID tenantId = TenantContext.require();
        materials.findByIdAndTenantId(id, tenantId).ifPresent(material -> {
            if (!material.isDeleted()) {
                dailyReports.findByIdAndTenantId(material.getDailyReportId(), tenantId)
                        .ifPresent(DailyReport::requireDraft);
            }
            material.markDeleted(actorUserId, clock.instant());
        });
        materials.flush();
    }

    /** Il rapportino deve essere un'anagrafica attiva dello stesso tenant, sempre restituita per il suo id. */
    private DailyReport requireValidDailyReport(UUID dailyReportId, UUID tenantId, boolean mustBeActive) {
        Optional<DailyReport> found = dailyReports.findByIdAndTenantId(dailyReportId, tenantId);
        if (mustBeActive && found.map(DailyReport::isDeleted).orElse(true)) {
            throw new UnknownDailyReportMaterialReferenceException("Rapportino non valido: " + dailyReportId);
        }
        return found.orElseThrow(
                () -> new UnknownDailyReportMaterialReferenceException("Rapportino non valido: " + dailyReportId));
    }
}
