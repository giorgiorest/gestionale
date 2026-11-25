import { Injectable, inject } from '@angular/core';
import { LOCAL_DB } from '../../db/local-db';

/** Chiave, nella tabella meta, del proprietario dei dati locali. */
const OWNER_KEY = 'owner';

/**
 * Dati personali conservati sul dispositivo (IndexedDB) per l'uso offline.
 *
 * Il database locale appartiene a un solo utente di una sola azienda: se sullo stesso browser
 * accede qualcun altro, i dati del precedente vengono cancellati prima di mostrare qualsiasi cosa.
 * All'uscita si cancellano sempre (vedi LogoutService).
 */
@Injectable({ providedIn: 'root' })
export class LocalDataService {
  private readonly db = inject(LOCAL_DB);

  /**
   * Associa i dati locali all'utente corrente (`tenantId:sub`).
   * Cancella tutto se appartenevano a un altro utente; restituisce true in quel caso.
   * Dati senza proprietario (versioni precedenti dell'app) vengono assegnati all'utente corrente.
   */
  async claim(owner: string): Promise<boolean> {
    const current = await this.db.meta.get(OWNER_KEY);
    if (current?.value === owner) {
      return false;
    }
    const wiped = !!current;
    if (wiped) {
      await this.clear();
    }
    await this.db.meta.put({ key: OWNER_KEY, value: owner });
    return wiped;
  }

  /** Modifiche non ancora inviate al server. */
  pendingChanges(): Promise<number> {
    return this.db.outbox.count();
  }

  /** Cancella tutti i dati locali (clienti, risorse, commesse, assegnazioni, notifiche, modifiche in attesa, stato della sincronizzazione). */
  async clear(): Promise<void> {
    const tables = [
      this.db.outbox,
      this.db.customers,
      this.db.resources,
      this.db.commesse,
      this.db.resourceAssignments,
      this.db.notifications,
      this.db.syncState,
      this.db.meta,
    ];
    await this.db.transaction('rw', tables, async () => {
      await Promise.all(tables.map((table) => table.clear()));
    });
  }
}

/** Identificativo del proprietario dei dati locali, dai claim del token: `tenant_id:sub`. */
export function ownerFromAccessToken(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(
      payload
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(payload.length / 4) * 4, '='),
    );
    const claims = JSON.parse(json) as { sub?: string; tenant_id?: string };
    return claims.sub ? `${claims.tenant_id ?? ''}:${claims.sub}` : null;
  } catch {
    return null;
  }
}
