import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, RapportinoLocale } from '../../db/db-locale';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nella outbox: coincide con il percorso dell'API (`PUT/DELETE /api/{entity}/{id}`). */
const ENTITY = 'daily-reports';

/** Rapportino come restituito dall'API. */
interface DailyReportDto {
  id: string;
  commessaId: string;
  date: string;
  weather: string | null;
  notes: string | null;
  status: string;
  submittedAt: string | null;
  closedAt: string | null;
  updatedAt: string;
}

/**
 * Testate dei rapportini giornalieri di una commessa, con copia locale per l'uso offline. Le
 * scritture passano dalla outbox ({@link SincronizzazioneService}), mai direttamente dall'API.
 *
 * <p>Come {@code LavorazioniService}, il caricamento è per singola commessa. Questa storia
 * crea/apre/salva solo un {@code DRAFT}: non c'è ancora azione di invio o chiusura.
 */
@Injectable({ providedIn: 'root' })
export class RapportiniService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SincronizzazioneService);
  private readonly profile = inject(ProfiloService);

  readonly reports = signal<RapportinoLocale[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi carica i rapportini della commessa indicata. */
  async load(commessaId: string): Promise<void> {
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(
        this.http.get<DailyReportDto[]>(`${this.apiUrl}/commesse/${commessaId}/daily-reports`),
      );
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.dailyReports.where('commessaId').equals(commessaId).toArray(),
      ]);
      // Un rapportino cancellato in locale ma non ancora inviato non deve tornare dal server.
      const arrived = remote.filter((r) => pending.get(r.id) !== 'delete');
      await this.db.dailyReports.bulkPut(
        arrived.map((r) => ({
          id: r.id,
          tenantId: tenantId ?? '',
          commessaId: r.commessaId,
          date: r.date,
          weather: r.weather,
          notes: r.notes,
          status: r.status,
          submittedAt: r.submittedAt,
          closedAt: r.closedAt,
          updatedAt: r.updatedAt,
        })),
      );
      // Toglie dalla copia locale i rapportini spariti dal server, salvo quelli ancora da inviare.
      const remoteIds = new Set(arrived.map((r) => r.id));
      const stale = local.filter((r) => !remoteIds.has(r.id) && !pending.has(r.id)).map((r) => r.id);
      await this.db.dailyReports.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(commessaId);
  }

  /** Crea la testata di un rapportino (sempre DRAFT), in locale e nella coda di invio al server. */
  async create(commessaId: string, date: string, weather: string | null, notes: string | null):
      Promise<RapportinoLocale> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const report: RapportinoLocale = {
      id: uuidv7(),
      tenantId,
      commessaId,
      date,
      weather: weather?.trim() || null,
      notes: notes?.trim() || null,
      status: 'draft',
      submittedAt: null,
      closedAt: null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.dailyReports.put(report);
    await this.sync.enqueue(ENTITY, 'create', {
      id: report.id,
      commessaId: report.commessaId,
      date: report.date,
      weather: report.weather,
      notes: report.notes,
    });
    await this.refresh(commessaId);
    void this.sync.flush();
    return report;
  }

  /**
   * Aggiorna meteo e note di un rapportino DRAFT già esistente, in locale e nella coda di invio.
   */
  async update(report: RapportinoLocale, weather: string | null, notes: string | null): Promise<void> {
    const updated: RapportinoLocale = {
      ...report,
      weather: weather?.trim() || null,
      notes: notes?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.dailyReports.put(updated);
    await this.sync.enqueue(ENTITY, 'update', {
      id: updated.id,
      commessaId: updated.commessaId,
      date: updated.date,
      weather: updated.weather,
      notes: updated.notes,
    });
    await this.refresh(updated.commessaId);
    void this.sync.flush();
  }

  /**
   * Cancella un rapportino: scompare subito dalla copia locale e la cancellazione viene accodata
   * (`DELETE /api/daily-reports/{id}`, idempotente). Sul server è logica.
   */
  async remove(id: string, commessaId: string): Promise<void> {
    await this.db.dailyReports.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(commessaId);
    void this.sync.flush();
  }

  private async refresh(commessaId: string): Promise<void> {
    const all = await this.db.dailyReports.where('commessaId').equals(commessaId).toArray();
    this.reports.set(all.sort((a, b) => b.date.localeCompare(a.date)));
  }
}
