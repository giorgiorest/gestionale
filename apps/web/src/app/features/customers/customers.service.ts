import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCustomer } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nelle API e nella outbox. */
const ENTITY = 'customers';

/** Cliente come restituito dall'API. */
interface CustomerDto {
  id: string;
  name: string;
  email: string | null;
  updatedAt: string;
}

/**
 * Clienti del tenant, con copia locale per l'uso offline.
 * Le scritture passano dalla outbox ({@link SyncService}), mai direttamente dall'API.
 */
@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly customers = signal<LocalCustomer[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);

  /** Invia le modifiche in sospeso, poi aggiorna la copia locale dal server (o la usa se offline). */
  async load(): Promise<void> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? null;
    await this.sync.flush();
    try {
      const remote = await firstValueFrom(this.http.get<CustomerDto[]>(`${this.apiUrl}/${ENTITY}`));
      const [pending, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.customers.toArray(),
      ]);
      // Un record cancellato in locale ma non ancora inviato non deve tornare dal server.
      const arrived = remote.filter((c) => pending.get(c.id) !== 'delete');
      await this.db.customers.bulkPut(
        arrived.map((c) => ({ id: c.id, tenantId: tenantId ?? '', name: c.name, email: c.email, updatedAt: c.updatedAt })),
      );
      // Toglie dalla copia locale i record spariti dal server, salvo quelli ancora da inviare.
      const remoteIds = new Set(remote.map((c) => c.id));
      const stale = local.filter((c) => !remoteIds.has(c.id) && !pending.has(c.id)).map((c) => c.id);
      await this.db.customers.bulkDelete(stale);
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh(tenantId);
  }

  /** Crea un cliente localmente e lo accoda per l'invio al server. */
  async create(name: string, email: string | null): Promise<LocalCustomer> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const customer: LocalCustomer = {
      id: uuidv7(),
      tenantId,
      name: name.trim(),
      email: email?.trim() || null,
      updatedAt: new Date().toISOString(),
    };
    await this.db.customers.put(customer);
    await this.sync.enqueue(ENTITY, 'create', { id: customer.id, name: customer.name, email: customer.email });
    await this.refresh(tenantId);
    void this.sync.flush();
    return customer;
  }

  /**
   * Cancella un cliente: scompare subito dalla copia locale e la cancellazione viene accodata
   * (`DELETE /api/customers/{id}`, idempotente). Sul server è una cancellazione logica.
   */
  async remove(id: string): Promise<void> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    await this.db.customers.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh(tenantId);
    void this.sync.flush();
  }

  private async refresh(tenantId: string | null): Promise<void> {
    const all = await this.db.customers.toArray();
    this.customers.set(
      all.filter((c) => !tenantId || c.tenantId === tenantId).sort((a, b) => a.name.localeCompare(b.name, 'it')),
    );
  }
}
