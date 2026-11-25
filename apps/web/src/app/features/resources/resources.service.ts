import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalResource } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nelle API e nella outbox. */
const ENTITY = 'resources';

/** Risorsa come restituita dall'API. */
interface ResourceDto {
  id: string;
  firstName: string;
  lastName: string;
  userId: string | null;
  teamLeadId: string | null;
  updatedAt: string;
}

/**
 * Risorse del tenant (dipendenti che compilano i rapportini), con copia locale per l'uso offline.
 * Le scritture passano dalla outbox ({@link SyncService}), mai direttamente dall'API.
 */
@Injectable({ providedIn: 'root' })
export class ResourcesService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly resources = signal<LocalResource[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi aggiorna la copia locale dal server (o la usa se offline). */
  async load(): Promise<void> {
    // flush() non dipende dal profilo (lavora solo sulla outbox locale): in parallelo, non in coda.
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(this.http.get<ResourceDto[]>(`${this.apiUrl}/${ENTITY}`));
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.resources.toArray(),
      ]);
      // Un record cancellato in locale ma non ancora inviato non deve tornare dal server.
      const arrived = remote.filter((r) => pending.get(r.id) !== 'delete');
      await this.db.resources.bulkPut(
        arrived.map((r) => ({
          id: r.id,
          tenantId: tenantId ?? '',
          firstName: r.firstName,
          lastName: r.lastName,
          userId: r.userId,
          teamLeadId: r.teamLeadId,
          updatedAt: r.updatedAt,
        })),
      );
      // Toglie dalla copia locale i record spariti dal server, salvo quelli ancora da inviare.
      const remoteIds = new Set(arrived.map((r) => r.id));
      const stale = local.filter((r) => !remoteIds.has(r.id) && !pending.has(r.id)).map((r) => r.id);
      await this.db.resources.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(tenantId);
  }

  /** Crea una risorsa localmente e la accoda per l'invio al server. */
  async create(firstName: string, lastName: string, teamLeadId: string | null): Promise<LocalResource> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const resource: LocalResource = {
      id: uuidv7(),
      tenantId,
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      userId: null,
      teamLeadId: teamLeadId || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.resources.put(resource);
    await this.sync.enqueue(ENTITY, 'create', {
      id: resource.id,
      firstName: resource.firstName,
      lastName: resource.lastName,
      userId: resource.userId,
      teamLeadId: resource.teamLeadId,
    });
    await this.refresh(tenantId);
    void this.sync.flush();
    return resource;
  }

  /**
   * Cancella una risorsa: scompare subito dalla copia locale e la cancellazione viene accodata
   * (`DELETE /api/resources/{id}`, idempotente). Sul server è una cancellazione logica.
   */
  async remove(id: string): Promise<void> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    await this.db.resources.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(tenantId);
    void this.sync.flush();
  }

  private async refresh(tenantId: string | null): Promise<void> {
    const all = await this.db.resources.toArray();
    this.resources.set(
      all
        .filter((r) => !tenantId || r.tenantId === tenantId)
        .sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`, 'it')),
    );
  }
}
