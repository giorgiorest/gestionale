import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';
import { ProfiloService } from '../profilo/profilo.service';
import { SincronizzazioneService } from '../sincronizzazione/sincronizzazione.service';
import { AllegatoLocale, CaricamentoInAttesa, LOCAL_DB } from '../../db/db-locale';
import { uuidv7 } from '../../shared/uuid';

/** Nome dell'entità nella outbox e nel percorso dell'API (`/api/attachments`). */
const ENTITY = 'attachments';

/**
 * Limiti controllati subito sul dispositivo, per non portarsi dietro un file che il server
 * rifiuterebbe. Rispecchiano i default del backend (`gestionale.attachments.*`); l'ultima parola
 * resta del server, che li valida di nuovo (ADR 0016 punto 7).
 */
export const DIMENSIONE_MASSIMA_BYTE = 25 * 1024 * 1024;
export const TIPI_AMMESSI = ['image/jpeg', 'image/png', 'image/heic', 'image/webp', 'application/pdf'];

/** Spazio massimo per i file scaricati o già caricati tenuti sul dispositivo (cache selettiva). */
export const CACHE_MASSIMA_BYTE = 100 * 1024 * 1024;

/** Alcuni telefoni non dichiarano il tipo di una foto HEIC o di un PDF: si ricava dall'estensione. */
const TIPO_DA_ESTENSIONE: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  heic: 'image/heic',
  webp: 'image/webp',
  pdf: 'application/pdf',
};

/** Allegato come lo mostra la UI: metadati più stato sul dispositivo. */
export interface AllegatoVista extends AllegatoLocale {
  /** true finché il file non è stato caricato e confermato sul server. */
  inAttesa: boolean;
  /** true se i byte sono sul dispositivo (si apre anche senza connessione). */
  disponibileOffline: boolean;
  /** Errore definitivo del server sul caricamento: il file non partirà più da solo. */
  errore: string | null;
}

/** Errore di validazione mostrabile all'utente (file non ammesso o troppo grande). */
export class AllegatoNonAmmessoError extends Error {}

/** SHA-256 esadecimale dei byte, come richiede la conferma del caricamento. */
export async function sha256Esadecimale(blob: Blob): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Tipo MIME di un file scelto: quello dichiarato dal sistema, altrimenti quello dell'estensione. */
export function tipoDelFile(file: File): string {
  if (file.type) {
    return file.type.toLowerCase();
  }
  const estensione = file.name.split('.').pop()?.toLowerCase() ?? '';
  return TIPO_DA_ESTENSIONE[estensione] ?? '';
}

/**
 * Allegati lato client (roadmap 2.5, ADR 0016).
 *
 * - **Caricamento offline**: il file scelto o scattato resta in `pendingUploads` (con i suoi byte)
 *   finché c'è campo; allora si chiede l'URL firmato, si caricano i byte direttamente
 *   sull'archivio e solo dopo si accoda la conferma dei metadati nella outbox. I byte non passano
 *   mai dall'API.
 * - **Cache selettiva**: i metadati si sincronizzano come ogni entità, i byte si scaricano solo
 *   quando l'utente apre un file e si tengono in `fileCache` fino a {@link CACHE_MASSIMA_BYTE}
 *   (esce chi è stato aperto meno di recente). I file in attesa di invio non escono mai.
 * - **Privacy**: tutte e tre le tabelle si svuotano all'uscita (`DatiLocaliService.clear()`).
 */
@Injectable({ providedIn: 'root' })
export class AllegatiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SincronizzazioneService);
  private readonly profile = inject(ProfiloService);

  /** Allegati del record caricato per ultimo con {@link load}. */
  readonly items = signal<AllegatoVista[]>([]);
  /** true se l'ultimo caricamento è avvenuto dalla copia locale (server non raggiungibile). */
  readonly offline = signal(false);
  /** Caricamenti in attesa di invio, per tutti i record. */
  readonly pendingUploads = signal(0);

  private uploading: Promise<number> | null = null;
  private current: { entityType: string; entityId: string } | null = null;

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => void this.uploadPending());
    }
  }

  /** Controlla tipo e dimensione; lancia {@link AllegatoNonAmmessoError} con un messaggio per l'utente. */
  validate(file: File): string {
    const mimeType = tipoDelFile(file);
    if (!TIPI_AMMESSI.includes(mimeType)) {
      throw new AllegatoNonAmmessoError('Tipo di file non ammesso: sono accettate foto (JPEG, PNG, HEIC, WebP) e PDF.');
    }
    if (file.size <= 0) {
      throw new AllegatoNonAmmessoError('Il file è vuoto.');
    }
    if (file.size > DIMENSIONE_MASSIMA_BYTE) {
      throw new AllegatoNonAmmessoError(`File troppo grande: il massimo è ${DIMENSIONE_MASSIMA_BYTE / 1024 / 1024} MB.`);
    }
    return mimeType;
  }

  /** Invia le modifiche in sospeso e i file in attesa, poi carica gli allegati del record indicato. */
  async load(entityType: string, entityId: string): Promise<void> {
    this.current = { entityType, entityId };
    const [profile] = await Promise.all([this.profile.load(), this.sync.flush()]);
    void this.uploadPending();
    try {
      const remote = await firstValueFrom(
        this.http.get<RemoteAllegato[]>(`${this.apiUrl}/${ENTITY}`, { params: { entityType, entityId } }),
      );
      const [pending, uploads, local] = await Promise.all([
        this.sync.pendingByEntity(ENTITY),
        this.db.pendingUploads.where('[entityType+entityId]').equals([entityType, entityId]).toArray(),
        this.db.attachments.where('[entityType+entityId]').equals([entityType, entityId]).toArray(),
      ]);
      // Un allegato cancellato in locale ma non ancora inviato non deve tornare dal server.
      const arrived = remote.filter((a) => pending.get(a.id) !== 'delete');
      await this.db.attachments.bulkPut(
        arrived.map((a) => ({
          id: a.id,
          tenantId: profile?.tenant?.id ?? '',
          entityType: a.entityType,
          entityId: a.entityId,
          originalFilename: a.originalFilename,
          mimeType: a.mimeType,
          sizeBytes: a.sizeBytes,
          sha256: a.sha256,
          updatedAt: a.updatedAt,
        })),
      );
      // Toglie dalla copia locale gli allegati spariti dal server, salvo quelli ancora da inviare.
      const remoteIds = new Set(arrived.map((a) => a.id));
      const waiting = new Set(uploads.map((u) => u.id));
      const stale = local.filter((a) => !remoteIds.has(a.id) && !waiting.has(a.id) && !pending.has(a.id));
      await this.db.attachments.bulkDelete(stale.map((a) => a.id));
      await Promise.all(stale.map((a) => this.db.fileCache.delete(a.id)));
      this.offline.set(false);
    } catch {
      this.offline.set(true);
    }
    await this.refresh();
  }

  /**
   * Aggiunge un file a un record: compare subito in elenco e parte quando c'è campo.
   * Lancia {@link AllegatoNonAmmessoError} se il file non è ammesso.
   */
  async add(entityType: string, entityId: string, file: File): Promise<void> {
    const mimeType = this.validate(file);
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    const sha256 = await sha256Esadecimale(file);
    const now = new Date().toISOString();
    const upload: CaricamentoInAttesa = {
      id: uuidv7(),
      entityType,
      entityId,
      filename: file.name || 'allegato',
      mimeType,
      sizeBytes: file.size,
      sha256,
      blob: file,
      createdAt: now,
      attempts: 0,
    };
    await this.db.pendingUploads.put(upload);
    await this.db.attachments.put({
      id: upload.id,
      tenantId,
      entityType,
      entityId,
      originalFilename: upload.filename,
      mimeType,
      sizeBytes: upload.sizeBytes,
      sha256,
      updatedAt: now,
    });
    await this.refresh();
    void this.uploadPending();
  }

  /**
   * Rimuove un allegato: sparisce subito da elenco e cache e la cancellazione viene accodata
   * (`DELETE /api/attachments/{id}`, idempotente, anche se il file non era ancora partito).
   */
  async remove(id: string): Promise<void> {
    await this.db.attachments.delete(id);
    await this.db.pendingUploads.delete(id);
    await this.db.fileCache.delete(id);
    await this.sync.enqueue(ENTITY, 'delete', { id });
    await this.refresh();
    void this.sync.flush();
  }

  /**
   * Byte dell'allegato, per aprirlo. Prima dal dispositivo (file in attesa o in cache), altrimenti
   * si scaricano dall'archivio con un URL firmato e si tengono in cache. Offline e non in cache:
   * lancia un errore con un messaggio per l'utente.
   */
  async fetchFile(attachment: AllegatoLocale): Promise<Blob> {
    const waiting = await this.db.pendingUploads.get(attachment.id);
    if (waiting) {
      return waiting.blob;
    }
    const cached = await this.db.fileCache.get(attachment.id);
    if (cached) {
      await this.db.fileCache.update(attachment.id, { lastAccess: Date.now() });
      return cached.blob;
    }
    try {
      const { downloadUrl } = await firstValueFrom(
        this.http.get<{ downloadUrl: string }>(`${this.apiUrl}/${ENTITY}/${attachment.id}/download-url`),
      );
      const response = await fetch(downloadUrl);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const blob = new Blob([await response.blob()], { type: attachment.mimeType });
      await this.keepInCache(attachment.id, blob);
      await this.refresh();
      return blob;
    } catch {
      throw new Error('File non disponibile: non è sul dispositivo e il server non è raggiungibile.');
    }
  }

  /** Carica sull'archivio i file in attesa e accoda la conferma. Restituisce quanti ne ha completati. */
  uploadPending(): Promise<number> {
    this.uploading ??= this.doUploadPending().finally(() => (this.uploading = null));
    return this.uploading;
  }

  private async doUploadPending(): Promise<number> {
    let done = 0;
    const waiting = await this.db.pendingUploads.orderBy('createdAt').toArray();
    for (const upload of waiting) {
      if (upload.lastError) {
        continue; // Rifiutato dal server: si attende che l'utente lo rimuova.
      }
      try {
        await this.upload(upload);
        done++;
      } catch (error) {
        const definitive = error instanceof HttpErrorResponse && !isTransient(error);
        await this.db.pendingUploads.update(upload.id, {
          attempts: upload.attempts + 1,
          ...(definitive ? { lastError: messaggioErrore(error) } : {}),
        });
        if (!definitive) {
          break; // Offline o archivio non raggiungibile: si riprova al prossimo giro.
        }
      }
    }
    await this.refresh();
    if (done > 0) {
      void this.sync.flush();
    }
    return done;
  }

  private async upload(upload: CaricamentoInAttesa): Promise<void> {
    const { uploadUrl } = await firstValueFrom(
      this.http.post<{ uploadUrl: string }>(`${this.apiUrl}/${ENTITY}/upload-url`, {
        id: upload.id,
        entityType: upload.entityType,
        entityId: upload.entityId,
        filename: upload.filename,
        mimeType: upload.mimeType,
        sizeBytes: upload.sizeBytes,
      }),
    );
    // Direttamente sull'archivio, senza Authorization: l'URL firmato è già il permesso. Il tipo
    // deve essere quello firmato dal server.
    const response = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': upload.mimeType },
      body: upload.blob,
    });
    if (!response.ok) {
      throw new Error(`Archivio: HTTP ${response.status}`);
    }
    // Rimosso nel frattempo (l'utente ha cancellato l'allegato): non si conferma ciò che non esiste più.
    if (!(await this.db.pendingUploads.get(upload.id))) {
      return;
    }
    await this.sync.enqueue(ENTITY, 'create', { id: upload.id, sha256: upload.sha256 });
    await this.keepInCache(upload.id, upload.blob);
    await this.db.pendingUploads.delete(upload.id);
  }

  /** Tiene i byte sul dispositivo e, se si supera il limite, toglie i file usati meno di recente. */
  private async keepInCache(id: string, blob: Blob): Promise<void> {
    await this.db.fileCache.put({ id, blob, sizeBytes: blob.size, lastAccess: Date.now() });
    const all = await this.db.fileCache.orderBy('lastAccess').toArray();
    let total = all.reduce((sum, f) => sum + f.sizeBytes, 0);
    for (const file of all) {
      if (total <= CACHE_MASSIMA_BYTE || file.id === id) {
        break;
      }
      await this.db.fileCache.delete(file.id);
      total -= file.sizeBytes;
    }
  }

  private async refresh(): Promise<void> {
    this.pendingUploads.set(await this.db.pendingUploads.count());
    if (!this.current) {
      return;
    }
    const key = [this.current.entityType, this.current.entityId];
    const [rows, uploads, cached] = await Promise.all([
      this.db.attachments.where('[entityType+entityId]').equals(key).toArray(),
      this.db.pendingUploads.where('[entityType+entityId]').equals(key).toArray(),
      this.db.fileCache.toArray(),
    ]);
    const byUpload = new Map(uploads.map((u) => [u.id, u]));
    const cachedIds = new Set(cached.map((f) => f.id));
    const items = rows
      // Una riga senza sha256 e senza file in attesa è un caricamento mai confermato: non si mostra.
      .filter((a) => a.sha256 !== null || byUpload.has(a.id))
      .map((a) => ({
        ...a,
        inAttesa: byUpload.has(a.id),
        disponibileOffline: byUpload.has(a.id) || cachedIds.has(a.id),
        errore: byUpload.get(a.id)?.lastError ?? null,
      }));
    this.items.set(items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)));
  }
}

/** Allegato come restituito da `GET /api/attachments`. */
interface RemoteAllegato {
  id: string;
  entityType: string;
  entityId: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  updatedAt: string;
}

function isTransient(error: HttpErrorResponse): boolean {
  return error.status === 0 || error.status >= 500 || [401, 408, 429].includes(error.status);
}

function messaggioErrore(error: HttpErrorResponse): string {
  const detail = (error.error as { detail?: string; message?: string } | null)?.detail
    ?? (error.error as { message?: string } | null)?.message;
  return detail ?? `Il server ha rifiutato il file (HTTP ${error.status}).`;
}
