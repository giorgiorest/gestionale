package it.gestionale.api.licensing;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "licenses")
public class License {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String plan;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Set<String> features = new HashSet<>();

    @Column(name = "ai_quota_monthly", nullable = false)
    private int aiQuotaMonthly;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    /** Token firmato, presente solo per le licenze offline. */
    @Column
    private String signature;

    protected License() {
    }

    public License(UUID id, UUID tenantId, String plan, int maxUsers, Set<String> features, int aiQuotaMonthly,
                   Instant validFrom, Instant validUntil) {
        this.id = id;
        this.tenantId = tenantId;
        this.plan = plan;
        this.maxUsers = maxUsers;
        this.features = new HashSet<>(features);
        this.aiQuotaMonthly = aiQuotaMonthly;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /** Modifica dalla console di piattaforma (solo licenze online, non firmate). */
    public void update(String plan, int maxUsers, Set<String> features, int aiQuotaMonthly, Instant validUntil) {
        this.plan = plan;
        this.maxUsers = maxUsers;
        this.features = new HashSet<>(features);
        this.aiQuotaMonthly = aiQuotaMonthly;
        this.validUntil = validUntil;
    }

    public String getPlan() {
        return plan;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public Set<String> getFeatures() {
        return Set.copyOf(features);
    }

    public int getAiQuotaMonthly() {
        return aiQuotaMonthly;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Entitlements toEntitlements() {
        return new Entitlements(plan, maxUsers, features, aiQuotaMonthly, validUntil);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
