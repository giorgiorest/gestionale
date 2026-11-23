import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCustomer } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

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
      const remote = await firstValueFrom(this.http.get<CustomerDto[]>(`${this.apiUrl}/customers`));
      const pendingIds = await this.pendingIds();
      const local = await this.db.customers.toArray();
      await this.db.customers.bulkPut(
        remote.map((c) => ({ id: c.id, tenantId: tenantId ?? '', name: c.name, email: c.email, updatedAt: c.updatedAt })),
      );
      // Toglie dalla copia locale i record spariti dal server, salvo quelli ancora da inviare.
      const remoteIds = new Set(remote.map((c) => c.id));
      const stale = local.filter((c) => !remoteIds.has(c.id) && !pendingIds.has(c.id)).map((c) => c.id);
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
    await this.sync.enqueue('customers', 'create', { id: customer.id, name: customer.name, email: customer.email });
    await this.refresh(tenantId);
    void this.sync.flush();
    return customer;
  }

  private async pendingIds(): Promise<Set<string>> {
    const entries = await this.db.outbox.toArray();
    return new Set(entries.map((e) => (e.payload as { id: string }).id));
  }

  private async refresh(tenantId: string | null): Promise<void> {
    const all = await this.db.customers.toArray();
    this.customers.set(
      all.filter((c) => !tenantId || c.tenantId === tenantId).sort((a, b) => a.name.localeCompare(b.name, 'it')),
    );
  }
}
