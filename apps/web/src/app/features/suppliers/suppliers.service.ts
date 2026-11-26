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
const ENTITY = 'suppliers';

export const DEFAULT_PAGE_SIZE = 50;

/** Fornitore come lo mostra la pagina (sia dall'API che dalla copia offline). */
export interface SupplierRow {
  id: string;
  name: string;
  email: string | null;
  partitaIva: string | null;
  codiceFiscale: string | null;
  indirizzo: string | null;
  pec: string | null;
  codiceSdi: string | null;
  condizioniPagamento: string | null;
  updatedAt: string;
}

/** Dati fiscali e commerciali in ingresso, oltre a ragione sociale ed email. */
export interface SupplierFiscalData {
  partitaIva?: string | null;
  codiceFiscale?: string | null;
  indirizzo?: string | null;
  pec?: string | null;
  codiceSdi?: string | null;
  condizioniPagamento?: string | null;
}

/**
 * Fornitori del tenant (roadmap 2.2): stesso schema di {@code CustomersService} ma tabella ed
 * endpoint propri — un fornitore non è un cliente con un'etichetta diversa.
 */
@Injectable({ providedIn: 'root' })
export class SuppliersService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly suppliers = signal<SupplierRow[]>([]);
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
        this.http.get<PageDto<SupplierRow>>(`${this.apiUrl}/${ENTITY}`, { params }),
      );
      // Una cancellazione locale non ancora inviata non deve tornare a comparire.
      const pending = await this.sync.pendingByEntity(ENTITY);
      this.suppliers.set(result.content.filter((s) => pending.get(s.id) !== 'delete'));
      this.totalElements.set(result.totalElements);
      this.offline.set(false);
    } catch {
      await this.loadOffline(q);
    }
  }

  private async loadOffline(q: string): Promise<void> {
    const needle = q.toLowerCase();
    const local = await this.db.suppliers.toArray();
    const matching = local
      .filter((s) => !this.tenantId || s.tenantId === this.tenantId)
      .filter((s) => !needle || s.name.toLowerCase().includes(needle) || (s.email ?? '').toLowerCase().includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name, 'it'));
    const start = this.page() * this.size();
    this.suppliers.set(matching.slice(start, start + this.size()));
    this.totalElements.set(matching.length);
    this.offline.set(true);
  }

  /** Crea un fornitore localmente e lo accoda per l'invio al server. */
  async create(name: string, email: string | null, fiscal: SupplierFiscalData = {}): Promise<SupplierRow> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const supplier: SupplierRow = {
      id: uuidv7(),
      name: name.trim(),
      email: email?.trim() || null,
      partitaIva: fiscal.partitaIva?.trim() || null,
      codiceFiscale: fiscal.codiceFiscale?.trim() || null,
      indirizzo: fiscal.indirizzo?.trim() || null,
      pec: fiscal.pec?.trim() || null,
      codiceSdi: fiscal.codiceSdi?.trim() || null,
      condizioniPagamento: fiscal.condizioniPagamento?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.suppliers.put({ ...supplier, tenantId });
    await this.sync.enqueue(ENTITY, 'create', {
      id: supplier.id,
      name: supplier.name,
      email: supplier.email,
      partitaIva: supplier.partitaIva,
      codiceFiscale: supplier.codiceFiscale,
      indirizzo: supplier.indirizzo,
      pec: supplier.pec,
      codiceSdi: supplier.codiceSdi,
      condizioniPagamento: supplier.condizioniPagamento,
    });
    this.suppliers.update((rows) => [...rows, supplier].sort((a, b) => a.name.localeCompare(b.name, 'it')));
    this.totalElements.update((n) => n + 1);
    void this.sync.flush();
    return supplier;
  }

  /**
   * Cancella un fornitore: scompare subito dalla pagina mostrata e la cancellazione viene accodata
   * (`DELETE /api/suppliers/{id}`, idempotente). Sul server è una cancellazione logica.
   */
  async remove(id: string): Promise<void> {
    await this.db.suppliers.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    this.suppliers.update((rows) => rows.filter((s) => s.id !== id));
    this.totalElements.update((n) => Math.max(0, n - 1));
    void this.sync.flush();
  }
}
