import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';

/** Ore totali di una settimana (vista settimanale per commessa). */
export interface WeeklyHours {
  weekStart: string;
  hours: number;
  overtimeHours: number;
}

/** Ore totali di una risorsa in un mese (vista mensile per risorsa). */
export interface ResourceHours {
  resourceId: string;
  month: string;
  hours: number;
  overtimeHours: number;
}

/** Ore totali di una lavorazione nel periodo. */
export interface WorkActivityHours {
  workActivityId: string;
  hours: number;
  overtimeHours: number;
}

/** Cruscotto ore come restituito dall'API (#52). */
export interface HoursReport {
  commessaId: string;
  from: string;
  to: string;
  weekly: WeeklyHours[];
  byResource: ResourceHours[];
  byWorkActivity: WorkActivityHours[];
}

/**
 * Cruscotto ore di una commessa (#52): report di sola lettura, aggregato dal backend. A
 * differenza degli altri servizi di questa cartella non ha copia locale né passa dalla outbox:
 * non è un'entità di dominio da sincronizzare offline, è una vista derivata, ricalcolata a ogni
 * richiesta (come la paginazione di ADR 0014, la sincronizzazione non la riguarda) — se il server
 * non è raggiungibile non c'è una copia da mostrare.
 */
@Injectable({ providedIn: 'root' })
export class HoursReportService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);

  readonly report = signal<HoursReport | null>(null);
  readonly loading = signal(false);
  /** true se l'ultimo tentativo di caricamento è andato in errore (server non raggiungibile o rifiutato). */
  readonly offline = signal(false);

  async load(commessaId: string, from: string, to: string): Promise<void> {
    this.loading.set(true);
    try {
      const params = new HttpParams().set('from', from).set('to', to);
      const result = await firstValueFrom(
        this.http.get<HoursReport>(`${this.apiUrl}/commesse/${commessaId}/reports/hours`, { params }),
      );
      this.report.set(result);
      this.offline.set(false);
    } catch {
      this.report.set(null);
      this.offline.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
