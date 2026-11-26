package it.gestionale.api.suppliers;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fornitore del tenant. L'id è generato dal client (UUID v7).
 *
 * <p>Tabella propria (non condivisa con {@code customers}), anche se i campi del mestiere sono gli
 * stessi: un fornitore non è un cliente con un'etichetta diversa, ed estensioni successive (Fase E
 * della roadmap: categoria, IBAN, referente) non devono toccare l'anagrafica clienti.
 *
 * <p>La cancellazione è <strong>logica</strong>: {@code deletedAt} valorizzato marca il record come
 * cancellato e lo lascia in tabella come tombstone, così la cancellazione può essere propagata ai
 * dispositivi che erano offline. {@code version} serve al blocco ottimistico.
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @Column(name = "partita_iva")
    private String partitaIva;

    @Column(name = "codice_fiscale")
    private String codiceFiscale;

    @Column
    private String indirizzo;

    @Column
    private String pec;

    @Column(name = "codice_sdi")
    private String codiceSdi;

    @Column(name = "condizioni_pagamento")
    private String condizioniPagamento;

    /** Blocco ottimistico: incrementata da Hibernate a ogni modifica. */
    @Version
    @Column(nullable = false)
    private long version;

    /** Valorizzata = record cancellato (tombstone per la sincronizzazione). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Claim {@code sub} di chi ha creato il record; null se scritto da un processo di sistema. */
    @Column(name = "created_by")
    private String createdBy;

    /** Claim {@code sub} di chi ha modificato o cancellato il record per ultimo. */
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Supplier() {
    }

    public Supplier(UUID id, UUID tenantId, String name, String email, String partitaIva, String codiceFiscale,
                     String indirizzo, String pec, String codiceSdi, String condizioniPagamento, String userId) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.partitaIva = partitaIva;
        this.codiceFiscale = codiceFiscale;
        this.indirizzo = indirizzo;
        this.pec = pec;
        this.codiceSdi = codiceSdi;
        this.condizioniPagamento = condizioniPagamento;
        this.createdBy = userId;
        this.updatedBy = userId;
    }

    /**
     * Aggiorna i dati modificabili. Un record cancellato torna attivo: l'id è scelto dal client, e
     * un {@code PUT} successivo alla cancellazione è l'ultima volontà del client (last-write-wins).
     * Data e autore della creazione non cambiano.
     */
    public void update(String name, String email, String partitaIva, String codiceFiscale, String indirizzo,
                        String pec, String codiceSdi, String condizioniPagamento, String userId, Instant at) {
        this.name = name;
        this.email = email;
        this.partitaIva = partitaIva;
        this.codiceFiscale = codiceFiscale;
        this.indirizzo = indirizzo;
        this.pec = pec;
        this.codiceSdi = codiceSdi;
        this.condizioniPagamento = condizioniPagamento;
        this.deletedAt = null;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    /**
     * Marca il record come cancellato. Non fa nulla se lo è già: la cancellazione è idempotente e
     * un reinvio dalla outbox non deve cambiare l'ora della cancellazione né quella di modifica.
     */
    public void markDeleted(String userId, Instant at) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedBy = userId;
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

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPartitaIva() {
        return partitaIva;
    }

    public String getCodiceFiscale() {
        return codiceFiscale;
    }

    public String getIndirizzo() {
        return indirizzo;
    }

    public String getPec() {
        return pec;
    }

    public String getCodiceSdi() {
        return codiceSdi;
    }

    public String getCondizioniPagamento() {
        return condizioniPagamento;
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
