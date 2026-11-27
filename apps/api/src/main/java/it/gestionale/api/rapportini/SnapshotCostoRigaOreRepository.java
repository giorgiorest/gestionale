package it.gestionale.api.rapportini;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo gli snapshot del tenant corrente. Registro di sola lettura
 * (nessun aggiornamento né cancellazione), scritto solo da {@code RigaOreService} alla
 * chiusura del rapportino.
 */
public interface SnapshotCostoRigaOreRepository extends JpaRepository<SnapshotCostoRigaOre, UUID> {

    List<SnapshotCostoRigaOre> findByDailyReportIdAndTenantId(UUID dailyReportId, UUID tenantId);
}
