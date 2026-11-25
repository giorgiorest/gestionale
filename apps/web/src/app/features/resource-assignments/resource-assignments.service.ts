import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalResourceAssignment } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nella outbox: coincide con il percorso dell'API (`PUT/DELETE /api/{entity}/{id}`). */
const ENTITY = 'resource-assignments';

/** Assegnazione come restituita dall'API. */
interface ResourceAssignmentDto {
  id: string;
  resourceId: string;
  commessaId: string;
  validFrom: string;
  validTo: string | null;
  role: string;
  hourlyCostOverride: number | null;
  active: boolean;
  updatedAt: string;
}

/**
 * Assegnazioni di risorse alle commesse (tab "Risorse" della commessa), con copia locale per
 * l'uso offline. Le scritture passano dalla outbox ({@link SyncService}), mai direttamente dall'API.
 *
 * <p>A differenza di {@code ResourcesService}/{@code CommesseService}, il caricamento è per
 * singola commessa: la lista di una commessa è quello che la UI mostra, non serve tenere in
 * locale le assegnazioni di tutte le commesse del tenant.
 */
@Injectable({ providedIn: 'root' })
export class ResourceAssignmentsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly assignments = signal<LocalResourceAssignment[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi carica le assegnazioni della commessa indicata. */
  async load(commessaId: string): Promise<void> {
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(
        this.http.get<ResourceAssignmentDto[]>(`${this.apiUrl}/commesse/${commessaId}/resource-assignments`),
      );
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.resourceAssignments.where('commessaId').equals(commessaId).toArray(),
      ]);
      // Un record cancellato in locale ma non ancora inviato non deve tornare dal server.
      const arrived = remote.filter((a) => pending.get(a.id) !== 'delete');
      await this.db.resourceAssignments.bulkPut(
        arrived.map((a) => ({
          id: a.id,
          tenantId: tenantId ?? '',
          resourceId: a.resourceId,
          commessaId: a.commessaId,
          validFrom: a.validFrom,
          validTo: a.validTo,
          role: a.role,
          hourlyCostOverride: a.hourlyCostOverride,
          active: a.active,
          updatedAt: a.updatedAt,
        })),
      );
      // Toglie dalla copia locale le assegnazioni sparite dal server, salvo quelle ancora da inviare.
      const remoteIds = new Set(arrived.map((a) => a.id));
      const stale = local.filter((a) => !remoteIds.has(a.id) && !pending.has(a.id)).map((a) => a.id);
      await this.db.resourceAssignments.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(commessaId);
  }

  /** Assegna una risorsa alla commessa, in locale e nella coda di invio al server. */
  async create(
    commessaId: string,
    resourceId: string,
    role: string,
    validFrom: string,
    validTo: string | null,
  ): Promise<LocalResourceAssignment> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const assignment: LocalResourceAssignment = {
      id: uuidv7(),
      tenantId,
      resourceId,
      commessaId,
      validFrom,
      validTo: validTo || null,
      role: role.trim(),
      hourlyCostOverride: null,
      active: true,
      updatedAt: new Date().toISOString(),
    };
    await this.db.resourceAssignments.put(assignment);
    await this.sync.enqueue(ENTITY, 'create', {
      id: assignment.id,
      resourceId: assignment.resourceId,
      commessaId: assignment.commessaId,
      validFrom: assignment.validFrom,
      validTo: assignment.validTo,
      role: assignment.role,
      hourlyCostOverride: assignment.hourlyCostOverride,
      active: assignment.active,
    });
    await this.refresh(commessaId);
    void this.sync.flush();
    return assignment;
  }

  /**
   * Cancella un'assegnazione: scompare subito dalla copia locale e la cancellazione viene
   * accodata (`DELETE /api/resource-assignments/{id}`, idempotente). Sul server è logica.
   */
  async remove(id: string, commessaId: string): Promise<void> {
    await this.db.resourceAssignments.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(commessaId);
    void this.sync.flush();
  }

  private async refresh(commessaId: string): Promise<void> {
    const all = await this.db.resourceAssignments.where('commessaId').equals(commessaId).toArray();
    this.assignments.set(all.sort((a, b) => a.validFrom.localeCompare(b.validFrom)));
  }
}
