package it.gestionale.api.workentries;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo gli snapshot del tenant corrente. Registro di sola lettura
 * (nessun aggiornamento né cancellazione), scritto solo da {@code WorkEntryService} alla
 * chiusura del rapportino.
 */
public interface WorkEntryCostSnapshotRepository extends JpaRepository<WorkEntryCostSnapshot, UUID> {

    List<WorkEntryCostSnapshot> findByDailyReportIdAndTenantId(UUID dailyReportId, UUID tenantId);
}
