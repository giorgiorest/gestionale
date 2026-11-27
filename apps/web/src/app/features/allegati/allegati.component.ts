import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatDialog } from '@angular/material/dialog';
import { AllegatiService, AllegatoNonAmmessoError, AllegatoVista, TIPI_AMMESSI } from '../../core/allegati/allegati.service';
import { HaPermessoDirective } from '../../core/profilo/ha-permesso.directive';
import { openConfirm } from '../../shared/dialogo-conferma';

/**
 * Allegati e foto di un record (rapportino, commessa, ...): elenco, scatto o scelta del file,
 * apertura, rimozione. Funziona anche senza connessione: i file aspettano sul dispositivo e partono
 * da soli (roadmap 2.5). I permessi dell'entità collegata li controlla il server.
 *
 * Uso: `<app-attachments entityType="daily_reports" [entityId]="report.id" />`
 */
@Component({
  selector: 'app-attachments',
  imports: [HaPermessoDirective, MatButtonModule, MatIconModule, MatListModule],
  template: `
    <div class="head">
      <h3>Foto e allegati</h3>
      <div class="actions" *appHasPermission="'attachments:write'">
        <!-- Fotocamera posteriore sul telefono; su PC apre la scelta del file. -->
        <input #camera hidden type="file" accept="image/*" capture="environment" (change)="pick($event)" />
        <input #files hidden type="file" multiple [accept]="accept" (change)="pick($event)" />
        <button mat-stroked-button type="button" (click)="camera.click()">
          <mat-icon>photo_camera</mat-icon>Scatta
        </button>
        <button mat-stroked-button type="button" (click)="files.click()">
          <mat-icon>attach_file</mat-icon>Aggiungi file
        </button>
      </div>
    </div>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (service.offline()) {
      <p class="muted">Server non raggiungibile: si vedono gli allegati salvati sul dispositivo.</p>
    }

    <mat-list data-testid="attachments">
      @for (a of service.items(); track a.id) {
        <mat-list-item>
          <mat-icon matListItemIcon>{{ a.mimeType === 'application/pdf' ? 'picture_as_pdf' : 'image' }}</mat-icon>
          <span matListItemTitle>{{ a.originalFilename }}</span>
          <span matListItemLine>{{ detail(a) }}</span>
          <span matListItemMeta class="row-actions">
            <button mat-icon-button type="button" aria-label="Apri" [disabled]="opening() === a.id" (click)="open(a)">
              <mat-icon>open_in_new</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'attachments:write'"
              aria-label="Rimuovi"
              (click)="remove(a)"
            >
              <mat-icon>delete_outline</mat-icon>
            </button>
          </span>
        </mat-list-item>
      } @empty {
        <p class="muted">Nessun allegato.</p>
      }
    </mat-list>
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
    }
    h3 {
      font: var(--mat-sys-title-small);
      margin: 0;
    }
    .actions {
      display: flex;
      gap: 8px;
    }
    .muted {
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      color: var(--mat-sys-error);
    }
    .row-actions {
      display: flex;
    }
  `,
})
export class AllegatiComponent {
  readonly entityType = input.required<string>();
  readonly entityId = input.required<string>();

  readonly service = inject(AllegatiService);
  private readonly dialog = inject(MatDialog);

  readonly accept = [...TIPI_AMMESSI, '.heic'].join(',');
  readonly error = signal<string | null>(null);
  readonly opening = signal<string | null>(null);

  constructor() {
    effect(() => {
      const [type, id] = [this.entityType(), this.entityId()];
      // Solo gli input fanno rieseguire il caricamento, non i signal letti dentro il servizio.
      untracked(() => void this.service.load(type, id));
    });
  }

  async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const chosen = Array.from(input.files ?? []);
    input.value = ''; // Permette di scegliere di nuovo lo stesso file.
    this.error.set(null);
    for (const file of chosen) {
      try {
        await this.service.add(this.entityType(), this.entityId(), file);
      } catch (e) {
        this.error.set(e instanceof AllegatoNonAmmessoError ? `${file.name}: ${e.message}` : `${file.name}: impossibile aggiungerlo.`);
      }
    }
  }

  detail(a: AllegatoVista): string {
    const size = a.sizeBytes >= 1024 * 1024 ? `${(a.sizeBytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(a.sizeBytes / 1024)} KB`;
    if (a.errore) {
      return `${size} · non caricato: ${a.errore}`;
    }
    if (a.inAttesa) {
      return `${size} · in attesa di invio`;
    }
    return a.disponibileOffline ? `${size} · disponibile offline` : size;
  }

  async open(a: AllegatoVista): Promise<void> {
    this.opening.set(a.id);
    this.error.set(null);
    try {
      const blob = await this.service.fetchFile(a);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.target = '_blank';
      link.rel = 'noopener';
      link.download = a.originalFilename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Impossibile aprire il file.');
    } finally {
      this.opening.set(null);
    }
  }

  async remove(a: AllegatoVista): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: "Rimuovere l'allegato?",
      message: `«${a.originalFilename}» non comparirà più in elenco. La rimozione viene inviata al server appena è raggiungibile.`,
      confirm: 'Rimuovi',
    });
    if (ok) {
      await this.service.remove(a.id);
    }
  }
}
