import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, LavorazioneLocale } from '../../db/db-locale';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nella outbox: coincide con il percorso dell'API (`PUT/DELETE /api/{entity}/{id}`). */
const ENTITY = 'work-activities';

/** Lavorazione come restituita dall'API. */
interface WorkActivityDto {
  id: string;
  commessaId: string;
  parentId: string | null;
  code: string;
  name: string;
  description: string | null;
  status: string;
  progressPercentage: number;
  updatedAt: string;
}

/**
 * Lavorazioni di una commessa (tab "Lavorazioni"), con copia locale per l'uso offline. Le
 * scritture passano dalla outbox ({@link SincronizzazioneService}), mai direttamente dall'API.
 *
 * <p>Come {@code AssegnazioniService}, il caricamento è per singola commessa.
 */
@Injectable({ providedIn: 'root' })
export class LavorazioniService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SincronizzazioneService);
  private readonly profile = inject(ProfiloService);

  readonly activities = signal<LavorazioneLocale[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi carica le lavorazioni della commessa indicata. */
  async load(commessaId: string): Promise<void> {
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(
        this.http.get<WorkActivityDto[]>(`${this.apiUrl}/commesse/${commessaId}/work-activities`),
      );
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.workActivities.where('commessaId').equals(commessaId).toArray(),
      ]);
      // Una lavorazione cancellata in locale ma non ancora inviata non deve tornare dal server.
      const arrived = remote.filter((a) => pending.get(a.id) !== 'delete');
      await this.db.workActivities.bulkPut(
        arrived.map((a) => ({
          id: a.id,
          tenantId: tenantId ?? '',
          commessaId: a.commessaId,
          parentId: a.parentId,
          code: a.code,
          name: a.name,
          description: a.description,
          status: a.status,
          progressPercentage: a.progressPercentage,
          updatedAt: a.updatedAt,
        })),
      );
      // Toglie dalla copia locale le lavorazioni sparite dal server, salvo quelle ancora da inviare.
      const remoteIds = new Set(arrived.map((a) => a.id));
      const stale = local.filter((a) => !remoteIds.has(a.id) && !pending.has(a.id)).map((a) => a.id);
      await this.db.workActivities.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(commessaId);
  }

  /** Crea una lavorazione della commessa, in locale e nella coda di invio al server. */
  async create(commessaId: string, code: string, name: string): Promise<LavorazioneLocale> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const activity: LavorazioneLocale = {
      id: uuidv7(),
      tenantId,
      commessaId,
      parentId: null,
      code: code.trim(),
      name: name.trim(),
      description: null,
      status: 'pianificata',
      progressPercentage: 0,
      updatedAt: new Date().toISOString(),
    };
    await this.db.workActivities.put(activity);
    await this.sync.enqueue(ENTITY, 'create', {
      id: activity.id,
      commessaId: activity.commessaId,
      parentId: activity.parentId,
      code: activity.code,
      name: activity.name,
      description: activity.description,
      status: activity.status,
      progressPercentage: activity.progressPercentage,
    });
    await this.refresh(commessaId);
    void this.sync.flush();
    return activity;
  }

  /**
   * Cancella una lavorazione: scompare subito dalla copia locale e la cancellazione viene
   * accodata (`DELETE /api/work-activities/{id}`, idempotente). Sul server è logica.
   */
  async remove(id: string, commessaId: string): Promise<void> {
    await this.db.workActivities.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(commessaId);
    void this.sync.flush();
  }

  private async refresh(commessaId: string): Promise<void> {
    const all = await this.db.workActivities.where('commessaId').equals(commessaId).toArray();
    this.activities.set(all.sort((a, b) => a.code.localeCompare(b.code, 'it')));
  }
}
