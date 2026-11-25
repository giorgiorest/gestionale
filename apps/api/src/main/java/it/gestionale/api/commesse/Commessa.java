package it.gestionale.api.commesse;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Commessa (il cantiere): l'entità centrale del prodotto (ADR 0012). L'id è generato dal client
 * (UUID v7). {@code customerId} è una chiave esterna vera verso {@code customers}: a differenza di
 * {@code Resource.userId}, i clienti sono un'anagrafica completa fin da subito, non si popola in
 * ritardo.
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Customer} (ADR 0018).
 */
@Entity
@Table(name = "commesse")
public class Commessa {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String nome;

    @Column
    private String indirizzo;

    @Convert(converter = CommessaStatoConverter.class)
    @Column(nullable = false)
    private CommessaStato stato = CommessaStato.PIANIFICATA;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Commessa() {
    }

    public Commessa(UUID id, UUID tenantId, UUID customerId, String nome, String indirizzo, CommessaStato stato,
                    String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.nome = nome;
        this.indirizzo = indirizzo;
        this.stato = stato;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(UUID customerId, String nome, String indirizzo, CommessaStato stato, String actorUserId,
                       Instant at) {
        this.customerId = customerId;
        this.nome = nome;
        this.indirizzo = indirizzo;
        this.stato = stato;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se la commessa è già cancellata. */
    public void markDeleted(String actorUserId, Instant at) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getNome() {
        return nome;
    }

    public String getIndirizzo() {
        return indirizzo;
    }

    public CommessaStato getStato() {
        return stato;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
