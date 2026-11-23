package it.gestionale.api.customers;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cliente del tenant. L'id è generato dal client (UUID). */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Customer() {
    }

    public Customer(UUID id, UUID tenantId, String name, String email) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
    }

    /** Aggiorna i dati modificabili. */
    public void update(String name, String email, Instant at) {
        this.name = name;
        this.email = email;
        this.updatedAt = at;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
