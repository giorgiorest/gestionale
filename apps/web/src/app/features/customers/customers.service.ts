import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { PageDto } from '../../core/api/page';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nelle API e nella outbox. */
const ENTITY = 'customers';

export const DEFAULT_PAGE_SIZE = 50;

/** Cliente come lo mostra la pagina (sia dall'API che dalla copia offline). */
export interface CustomerRow {
  id: string;
  name: string;
  email: string | null;
  updatedAt: string;
}

/**
 * Clienti del tenant: lista paginata e ricercabile lato server (ADR 0014), con copia offline.
 *
 * - Online, `load()` chiama `GET /api/customers?page=&size=&q=`: la pagina mostrata è quella del
 *   server, non più "tutto l'elenco".
 * - Offline, ricade sulla copia locale che il cursore di sincronizzazione mantiene in `db.customers`
 *   (vedi {@link SyncService.pullChanges}), con paginazione e ricerca calcolate lato client: è
 *   un'esperienza degradata, non un secondo meccanismo di sincronizzazione.
 * - Le scritture passano dalla outbox ({@link SyncService}), mai direttamente dall'API, e
 *   aggiornano la pagina corrente in modo ottimistico (senza aspettare un nuovo giro dal server).
 */
@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly customers = signal<CustomerRow[]>([]);
  readonly totalElements = signal(0);
  readonly page = signal(0);
  readonly size = signal(DEFAULT_PAGE_SIZE);
  readonly q = signal('');
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  private tenantId: string | null = null;

  /** Invia le modifiche in sospeso, poi carica la pagina corrente dal server (o dalla copia locale). */
  async load(): Promise<void> {
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    this.tenantId = profile?.tenant?.id ?? null;
    const q = this.q().trim();
    const params: Record<string, string> = { page: String(this.page()), size: String(this.size()) };
    if (q) {
      params['q'] = q;
    }
    try {
      const result = await firstValueFrom(
        this.http.get<PageDto<CustomerRow>>(`${this.apiUrl}/${ENTITY}`, { params }),
      );
      // Una cancellazione locale non ancora inviata non deve tornare a comparire.
      const pending = await this.sync.pendingByEntity(ENTITY);
      this.customers.set(result.content.filter((c) => pending.get(c.id) !== 'delete'));
      this.totalElements.set(result.totalElements);
      this.offline.set(false);
    } catch {
      await this.loadOffline(q);
    }
  }

  private async loadOffline(q: string): Promise<void> {
    const needle = q.toLowerCase();
    const local = await this.db.customers.toArray();
    const matching = local
      .filter((c) => !this.tenantId || c.tenantId === this.tenantId)
      .filter((c) => !needle || c.name.toLowerCase().includes(needle) || (c.email ?? '').toLowerCase().includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name, 'it'));
    const start = this.page() * this.size();
    this.customers.set(matching.slice(start, start + this.size()));
    this.totalElements.set(matching.length);
    this.offline.set(true);
  }

  /** Crea un cliente localmente e lo accoda per l'invio al server. */
  async create(name: string, email: string | null): Promise<CustomerRow> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const customer: CustomerRow = {
      id: uuidv7(),
      name: name.trim(),
      email: email?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.customers.put({ ...customer, tenantId });
    await this.sync.enqueue(ENTITY, 'create', { id: customer.id, name: customer.name, email: customer.email });
    this.customers.update((rows) => [...rows, customer].sort((a, b) => a.name.localeCompare(b.name, 'it')));
    this.totalElements.update((n) => n + 1);
    void this.sync.flush();
    return customer;
  }

  /**
   * Cancella un cliente: scompare subito dalla pagina mostrata e la cancellazione viene accodata
   * (`DELETE /api/customers/{id}`, idempotente). Sul server è una cancellazione logica.
   */
  async remove(id: string): Promise<void> {
    await this.db.customers.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    this.customers.update((rows) => rows.filter((c) => c.id !== id));
    this.totalElements.update((n) => Math.max(0, n - 1));
    void this.sync.flush();
  }
}
