package it.gestionale.api.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Convert(converter = EnumMinuscoloConverters.KindConverter.class)
    @Column(nullable = false)
    private TipoTenant kind;

    @Convert(converter = EnumMinuscoloConverters.StatusConverter.class)
    @Column(nullable = false)
    private StatoTenant status = StatoTenant.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(UUID id, String name, String slug, TipoTenant kind) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.kind = kind;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public TipoTenant getKind() {
        return kind;
    }

    public StatoTenant getStatus() {
        return status;
    }

    public void setStatus(StatoTenant status) {
        this.status = status;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
