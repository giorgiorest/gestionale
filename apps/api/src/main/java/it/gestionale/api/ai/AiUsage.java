package it.gestionale.api.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una chiamata registrata all'assistente: tenant, utente, modello, token e costo stimato. */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostEur;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiUsage() {
    }

    public AiUsage(UUID tenantId, String userId, AiResponse response, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.model = response.model();
        this.inputTokens = response.inputTokens();
        this.outputTokens = response.outputTokens();
        this.estimatedCostEur = response.estimatedCostEur() == null ? BigDecimal.ZERO : response.estimatedCostEur();
        this.createdAt = createdAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getModel() {
        return model;
    }
}
