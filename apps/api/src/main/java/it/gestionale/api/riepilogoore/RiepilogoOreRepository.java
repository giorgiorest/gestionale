package it.gestionale.api.riepilogoore;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.rapportini.RigaOre;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Sola lettura, per il cruscotto ore (#52): aggrega le righe di ore di una commessa, già filtrate
 * per tenant e periodo. {@code RigaOre} e {@code Rapportino} non hanno una relazione JPA (come
 * altrove nel modulo, i riferimenti sono id semplici): il join è nella clausola {@code where},
 * con entrambe le entità nella {@code from} (consentito da JPQL).
 *
 * <p>Il {@code GROUP BY} è per giorno/risorsa/lavorazione, il livello più fine utile: le tre viste
 * del cruscotto (settimanale, mensile per risorsa, per lavorazione) sono poi un raggruppamento più
 * grossolano di queste righe già somma in {@link RiepilogoOreService}, non una nuova somma su dati
 * non aggregati.
 */
public interface RiepilogoOreRepository extends Repository<RigaOre, UUID> {

    @Query("""
            select dr.date as date, we.resourceId as resourceId, we.workActivityId as workActivityId,
                   sum(we.hours) as hours, sum(we.overtimeHours) as overtimeHours
            from RigaOre we, Rapportino dr
            where we.dailyReportId = dr.id
              and dr.commessaId = :commessaId and dr.tenantId = :tenantId and we.tenantId = :tenantId
              and dr.date between :from and :to
              and we.deletedAt is null and dr.deletedAt is null
            group by dr.date, we.resourceId, we.workActivityId
            """)
    List<AggregatoOreGiornaliere> aggregateByDateResourceAndActivity(@Param("commessaId") UUID commessaId,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("tenantId") UUID tenantId);
}
