import { DecimalPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { BrandingService } from '../../core/branding/branding.service';
import { isPackagedDesktopApp } from '../../core/platform';

/** Un installer pubblicato in downloads/ (vedi infra/docker/web/prepare-desktop-downloads.sh). */
export interface DesktopFile {
  file: string;
  size: number;
  sha256: string;
}

/** Contenuto di downloads/desktop.json, scritto dalla pipeline di rilascio. */
export interface DesktopRelease {
  version: string;
  commit: string;
  date: string;
  setup: DesktopFile;
  msi: DesktopFile;
}

/**
 * Pagina pubblica di download dell'app desktop per Windows.
 *
 * Gli installer sono nell'immagine web dello stesso rilascio (Release immagini): ogni server
 * offre l'app della propria versione. Un solo installer per tutti i clienti: al primo avvio
 * l'utente indica l'indirizzo di questo server, mostrato qui da copiare.
 */
@Component({
  selector: 'app-desktop-download',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
  ],
  template: `
    <div class="page narrow">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-icon mat-card-avatar>desktop_windows</mat-icon>
          <mat-card-title>App per Windows</mat-card-title>
          <mat-card-subtitle
            >{{ branding.name() }} sul tuo PC, anche senza connessione</mat-card-subtitle
          >
        </mat-card-header>
        <mat-card-content>
          @if (desktop) {
            <p>Stai già usando l'app desktop.</p>
          } @else if (loading()) {
            <mat-spinner diameter="32" />
          } @else if (release(); as r) {
            <div class="download">
              <a
                mat-flat-button
                [href]="'downloads/' + r.setup.file"
                download
                data-testid="download-setup"
              >
                <mat-icon>download</mat-icon>Scarica per Windows
              </a>
              <span class="muted">
                Versione {{ r.version }} · {{ r.date | date: 'd MMMM y' }} ·
                {{ r.setup.size / 1048576 | number: '1.0-1' }} MB
              </span>
            </div>

            <h2>Installazione</h2>
            <ol class="steps">
              <li>Apri il file scaricato. Non servono permessi di amministratore.</li>
              <li>
                Se Windows mostra <strong>«PC protetto da Windows»</strong>, scegli
                <strong>Ulteriori informazioni</strong> e poi <strong>Esegui comunque</strong>:
                l'app non è ancora firmata digitalmente.
              </li>
              <li>
                Al primo avvio l'app chiede l'indirizzo del server. Inserisci:
                <div class="server">
                  <code data-testid="server-url">{{ serverUrl }}</code>
                  <button mat-stroked-button (click)="copy()">
                    <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>{{
                      copied() ? 'Copiato' : 'Copia'
                    }}
                  </button>
                </div>
              </li>
              <li>Accedi con le stesse credenziali che usi nel browser.</li>
            </ol>

            <h2>Requisiti</h2>
            <p>
              Windows 10 o 11 a 64 bit. Se manca il componente Microsoft Edge WebView2,
              l'installazione lo scarica da sola.
            </p>

            <h2>Per il reparto IT</h2>
            <p>
              Pacchetto MSI per l'installazione centralizzata:
              <a [href]="'downloads/' + r.msi.file" download data-testid="download-msi">{{
                r.msi.file
              }}</a>
              ({{ r.msi.size / 1048576 | number: '1.0-1' }} MB).
            </p>
            <p class="muted hash">
              SHA-256 {{ r.setup.file }}: {{ r.setup.sha256 }}<br />
              SHA-256 {{ r.msi.file }}: {{ r.msi.sha256 }}
            </p>
          } @else {
            <p data-testid="not-available">
              Il download dell'app desktop non è disponibile su questo server. Puoi usare
              {{ branding.name() }} dal browser, anche installandolo come app dal menu del browser.
            </p>
          }
        </mat-card-content>
        <mat-card-actions align="end">
          <a mat-button routerLink="/"><mat-icon>arrow_back</mat-icon>Torna all'applicazione</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: `
    .narrow {
      max-width: 800px;
      margin: 0 auto;
    }
    mat-card-header mat-icon {
      color: var(--mat-sys-primary);
    }
    .download {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px;
      margin: 16px 0;
    }
    h2 {
      font: var(--mat-sys-title-medium);
      margin: 24px 0 8px;
    }
    .steps li {
      margin-bottom: 12px;
    }
    .server {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-top: 8px;
    }
    code {
      font: var(--mat-sys-body-large);
      padding: 6px 12px;
      border-radius: var(--mat-sys-corner-small);
      background: var(--mat-sys-surface-container-high);
    }
    .hash {
      font: var(--mat-sys-body-small);
      overflow-wrap: anywhere;
    }
    a:not([mat-flat-button]):not([mat-button]) {
      color: var(--mat-sys-primary);
    }
  `,
})
export class DesktopDownloadPage {
  readonly branding = inject(BrandingService);
  private readonly clipboard = inject(Clipboard);

  /** Nell'app desktop la pagina non offre il download. */
  readonly desktop = isPackagedDesktopApp();
  readonly loading = signal(!this.desktop);
  readonly release = signal<DesktopRelease | null>(null);
  readonly copied = signal(false);

  /** Indirizzo da inserire al primo avvio dell'app: questo sito (con l'eventuale percorso base). */
  readonly serverUrl = new URL('.', document.baseURI).href.replace(/\/$/, '');

  constructor() {
    if (!this.desktop) {
      void this.load();
    }
  }

  copy(): void {
    this.copied.set(this.clipboard.copy(this.serverUrl));
  }

  private async load(): Promise<void> {
    try {
      const response = await fetch(new URL('downloads/desktop.json', document.baseURI).href, {
        cache: 'no-cache',
      });
      const release = response.ok ? ((await response.json()) as DesktopRelease) : null;
      this.release.set(release?.setup?.file && release.msi?.file ? release : null);
    } catch {
      this.release.set(null);
    } finally {
      this.loading.set(false);
    }
  }
}
