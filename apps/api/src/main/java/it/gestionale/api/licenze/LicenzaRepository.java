package it.gestionale.api.licenze;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LicenzaRepository extends JpaRepository<Licenza, UUID> {

    /** Licenze valide all'istante indicato, la più duratura per prima. Filtrate anche dalla RLS. */
    @Query("""
            select l from Licenza l
            where l.tenantId = :tenantId
              and l.validFrom <= :at
              and (l.validUntil is null or l.validUntil > :at)
            order by l.validUntil desc nulls first
            """)
    List<Licenza> findValid(@Param("tenantId") UUID tenantId, @Param("at") Instant at);

    void deleteByTenantId(UUID tenantId);

    /** Tutte le licenze del tenant, la più recente per prima (console di piattaforma). */
    List<Licenza> findByTenantIdOrderByValidFromDesc(UUID tenantId);
}
