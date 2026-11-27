import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, RigaOreLocale } from '../../db/db-locale';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nella outbox: coincide con il percorso dell'API (`PUT/DELETE /api/{entity}/{id}`). */
const ENTITY = 'work-entries';

/** Riga di ore come restituita dall'API. */
interface WorkEntryDto {
  id: string;
  dailyReportId: string;
  resourceId: string;
  workActivityId: string;
  hours: number;
  overtimeHours: number;
  notes: string | null;
  updatedAt: string;
}

/**
 * Righe di ore di un rapportino giornaliero (#50), con copia locale per l'uso offline. Le
 * scritture passano dalla outbox ({@link SincronizzazioneService}), mai direttamente dall'API.
 *
 * <p>Come {@code RapportiniService}, il caricamento è per singolo rapportino: la lista di un
 * rapportino è quello che la UI mostra, non serve tenere in locale le righe di tutti i rapportini.
 */
@Injectable({ providedIn: 'root' })
export class RigheOreService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SincronizzazioneService);
  private readonly profile = inject(ProfiloService);

  readonly entries = signal<RigaOreLocale[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi carica le righe del rapportino indicato. */
  async load(dailyReportId: string): Promise<void> {
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(
        this.http.get<WorkEntryDto[]>(`${this.apiUrl}/daily-reports/${dailyReportId}/work-entries`),
      );
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.workEntries.where('dailyReportId').equals(dailyReportId).toArray(),
      ]);
      // Una riga cancellata in locale ma non ancora inviata non deve tornare dal server.
      const arrived = remote.filter((e) => pending.get(e.id) !== 'delete');
      await this.db.workEntries.bulkPut(
        arrived.map((e) => ({
          id: e.id,
          tenantId: tenantId ?? '',
          dailyReportId: e.dailyReportId,
          resourceId: e.resourceId,
          workActivityId: e.workActivityId,
          hours: e.hours,
          overtimeHours: e.overtimeHours,
          notes: e.notes,
          updatedAt: e.updatedAt,
        })),
      );
      // Toglie dalla copia locale le righe sparite dal server, salvo quelle ancora da inviare.
      const remoteIds = new Set(arrived.map((e) => e.id));
      const stale = local.filter((e) => !remoteIds.has(e.id) && !pending.has(e.id)).map((e) => e.id);
      await this.db.workEntries.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(dailyReportId);
  }

  /** Crea una riga di ore del rapportino, in locale e nella coda di invio al server. */
  async create(
    dailyReportId: string,
    resourceId: string,
    workActivityId: string,
    hours: number,
    overtimeHours: number,
    notes: string | null,
  ): Promise<RigaOreLocale> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const entry: RigaOreLocale = {
      id: uuidv7(),
      tenantId,
      dailyReportId,
      resourceId,
      workActivityId,
      hours,
      overtimeHours,
      notes: notes?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.workEntries.put(entry);
    await this.sync.enqueue(ENTITY, 'create', {
      id: entry.id,
      dailyReportId: entry.dailyReportId,
      resourceId: entry.resourceId,
      workActivityId: entry.workActivityId,
      hours: entry.hours,
      overtimeHours: entry.overtimeHours,
      notes: entry.notes,
    });
    await this.refresh(dailyReportId);
    void this.sync.flush();
    return entry;
  }

  /** Aggiorna una riga di ore già esistente, in locale e nella coda di invio. */
  async update(
    entry: RigaOreLocale,
    resourceId: string,
    workActivityId: string,
    hours: number,
    overtimeHours: number,
    notes: string | null,
  ): Promise<void> {
    const updated: RigaOreLocale = {
      ...entry,
      resourceId,
      workActivityId,
      hours,
      overtimeHours,
      notes: notes?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.workEntries.put(updated);
    await this.sync.enqueue(ENTITY, 'update', {
      id: updated.id,
      dailyReportId: updated.dailyReportId,
      resourceId: updated.resourceId,
      workActivityId: updated.workActivityId,
      hours: updated.hours,
      overtimeHours: updated.overtimeHours,
      notes: updated.notes,
    });
    await this.refresh(updated.dailyReportId);
    void this.sync.flush();
  }

  /**
   * Cancella una riga: scompare subito dalla copia locale e la cancellazione viene accodata
   * (`DELETE /api/work-entries/{id}`, idempotente). Sul server è logica.
   */
  async remove(id: string, dailyReportId: string): Promise<void> {
    await this.db.workEntries.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(dailyReportId);
    void this.sync.flush();
  }

  private async refresh(dailyReportId: string): Promise<void> {
    const all = await this.db.workEntries.where('dailyReportId').equals(dailyReportId).toArray();
    this.entries.set(all.sort((a, b) => a.updatedAt.localeCompare(b.updatedAt)));
  }
}
