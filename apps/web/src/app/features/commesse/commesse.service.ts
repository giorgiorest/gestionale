import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCommessa } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nelle API e nella outbox. */
const ENTITY = 'commesse';

/** Etichette degli stati, nell'ordine naturale del ciclo di vita della commessa. */
export const COMMESSA_STATI: { value: string; label: string }[] = [
  { value: 'pianificata', label: 'Pianificata' },
  { value: 'in_corso', label: 'In corso' },
  { value: 'sospesa', label: 'Sospesa' },
  { value: 'conclusa', label: 'Conclusa' },
];

/** Commessa come restituita dall'API. */
interface CommessaDto {
  id: string;
  customerId: string;
  nome: string;
  indirizzo: string | null;
  stato: string;
  updatedAt: string;
}

/**
 * Commesse (cantieri) del tenant, con copia locale per l'uso offline.
 * Le scritture passano dalla outbox ({@link SyncService}), mai direttamente dall'API.
 */
@Injectable({ providedIn: 'root' })
export class CommesseService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly commesse = signal<LocalCommessa[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi aggiorna la copia locale dal server (o la usa se offline). */
  async load(): Promise<void> {
    // flush() non dipende dal profilo (lavora solo sulla outbox locale): in parallelo, non in coda.
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    const tenantId = profile?.tenant?.id ?? null;
    try {
      const remote = await firstValueFrom(this.http.get<CommessaDto[]>(`${this.apiUrl}/${ENTITY}`));
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.commesse.toArray(),
      ]);
      // Una commessa cancellata in locale ma non ancora inviata non deve tornare dal server.
      const arrived = remote.filter((c) => pending.get(c.id) !== 'delete');
      await this.db.commesse.bulkPut(
        arrived.map((c) => ({
          id: c.id,
          tenantId: tenantId ?? '',
          customerId: c.customerId,
          nome: c.nome,
          indirizzo: c.indirizzo,
          stato: c.stato,
          updatedAt: c.updatedAt,
        })),
      );
      // Toglie dalla copia locale le commesse sparite dal server, salvo quelle ancora da inviare.
      const remoteIds = new Set(arrived.map((c) => c.id));
      const stale = local.filter((c) => !remoteIds.has(c.id) && !pending.has(c.id)).map((c) => c.id);
      await this.db.commesse.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(tenantId);
  }

  /** Crea una commessa localmente e la accoda per l'invio al server. */
  async create(customerId: string, nome: string, indirizzo: string | null, stato: string): Promise<LocalCommessa> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const commessa: LocalCommessa = {
      id: uuidv7(),
      tenantId,
      customerId,
      nome: nome.trim(),
      indirizzo: indirizzo?.trim() || null,
      stato,
      updatedAt: new Date().toISOString(),
    };
    await this.db.commesse.put(commessa);
    await this.sync.enqueue(ENTITY, 'create', {
      id: commessa.id,
      customerId: commessa.customerId,
      nome: commessa.nome,
      indirizzo: commessa.indirizzo,
      stato: commessa.stato,
    });
    await this.refresh(tenantId);
    void this.sync.flush();
    return commessa;
  }

  /**
   * Cancella una commessa: scompare subito dalla copia locale e la cancellazione viene accodata
   * (`DELETE /api/commesse/{id}`, idempotente). Sul server è una cancellazione logica.
   */
  async remove(id: string): Promise<void> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    await this.db.commesse.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(tenantId);
    void this.sync.flush();
  }

  private async refresh(tenantId: string | null): Promise<void> {
    const all = await this.db.commesse.toArray();
    this.commesse.set(
      all.filter((c) => !tenantId || c.tenantId === tenantId).sort((a, b) => a.nome.localeCompare(b.nome, 'it')),
    );
  }
}
