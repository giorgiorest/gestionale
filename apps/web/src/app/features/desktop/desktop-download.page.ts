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

/** Un pacchetto pubblicato in downloads/ (vedi infra/docker/web/prepare-desktop-downloads.sh). */
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
  /** APK Android: manca se la build Android di quel rilascio non è riuscita. */
  android?: DesktopFile;
}

/**
 * Pagina pubblica di download dell'app installabile: Windows e Android.
 *
 * I pacchetti sono nell'immagine web dello stesso rilascio (Rilascio): ogni server
 * offre l'app della propria versione. Un solo pacchetto per tutti i clienti: al primo avvio
 * l'utente indica l'indirizzo di questo server, mostrato qui da copiare.
 *
 * L'APK è facoltativo: se la build Android non è riuscita la pagina mostra solo Windows.
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
          <mat-icon mat-card-avatar>devices</mat-icon>
          <mat-card-title>App per Windows e Android</mat-card-title>
          <mat-card-subtitle
            >{{ branding.name() }} sul tuo PC o sul telefono, anche senza
            connessione</mat-card-subtitle
          >
        </mat-card-header>
        <mat-card-content>
          @if (desktop) {
            <p>Stai già usando l'app installata.</p>
          } @else if (loading()) {
            <mat-spinner diameter="32" />
          } @else if (release(); as r) {
            <p>
              Versione {{ r.version }} · {{ r.date | date: 'd MMMM y' }}. Al primo avvio l'app
              chiede l'<strong>indirizzo del server</strong>, lo stesso per Windows e Android:
            </p>
            <div class="server">
              <code data-testid="server-url">{{ serverUrl }}</code>
              <button mat-stroked-button (click)="copy()">
                <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>{{
                  copied() ? 'Copiato' : 'Copia'
                }}
              </button>
            </div>

            <h2><mat-icon>desktop_windows</mat-icon>Windows</h2>
            <div class="download">
              <a
                mat-flat-button
                [href]="'downloads/' + r.setup.file"
                download
                data-testid="download-setup"
              >
                <mat-icon>download</mat-icon>Scarica per Windows
              </a>
              <span class="muted">{{ r.setup.size / 1048576 | number: '1.0-1' }} MB</span>
            </div>
            <ol class="steps">
              <li>Apri il file scaricato. Non servono permessi di amministratore.</li>
              <li>
                Se Windows mostra <strong>«PC protetto da Windows»</strong>, scegli
                <strong>Ulteriori informazioni</strong> e poi <strong>Esegui comunque</strong>:
                l'app non è ancora firmata digitalmente.
              </li>
              <li>
                Inserisci l'indirizzo del server qui sopra e accedi con le solite credenziali.
              </li>
            </ol>
            <p class="muted">
              Serve Windows 10 o 11 a 64 bit. Se manca il componente Microsoft Edge WebView2,
              l'installazione lo scarica da sola.
            </p>
            <p>
              Per il reparto IT, pacchetto MSI per l'installazione centralizzata:
              <a [href]="'downloads/' + r.msi.file" download data-testid="download-msi">{{
                r.msi.file
              }}</a>
              ({{ r.msi.size / 1048576 | number: '1.0-1' }} MB).
            </p>

            <h2><mat-icon>android</mat-icon>Android</h2>
            @if (r.android; as apk) {
              <div class="download">
                <a
                  mat-flat-button
                  [href]="'downloads/' + apk.file"
                  download
                  data-testid="download-apk"
                >
                  <mat-icon>download</mat-icon>Scarica per Android
                </a>
                <span class="muted">{{ apk.size / 1048576 | number: '1.0-1' }} MB</span>
              </div>
              <ol class="steps">
                <li>Apri il file scaricato (notifica di download o app <em>File</em>).</li>
                <li>
                  Android chiede di <strong>consentire l'installazione da questa origine</strong>:
                  l'app non arriva dal Play Store. Il permesso vale solo per il browser e si può
                  togliere subito dopo.
                </li>
                <li>
                  Inserisci l'indirizzo del server qui sopra e accedi con le solite credenziali.
                </li>
              </ol>
              <p class="muted">
                Serve Android 7 o successivo. Gli aggiornamenti non sono automatici: torna qui e
                scarica di nuovo.
              </p>
            } @else {
              <p class="muted" data-testid="android-not-available">
                L'app Android non è disponibile per questa versione del server.
              </p>
            }

            <p class="muted hash">
              SHA-256 {{ r.setup.file }}: {{ r.setup.sha256 }}<br />
              SHA-256 {{ r.msi.file }}: {{ r.msi.sha256 }}
              @if (r.android; as apk) {
                <br />SHA-256 {{ apk.file }}: {{ apk.sha256 }}
              }
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
      display: flex;
      align-items: center;
      gap: 8px;
      font: var(--mat-sys-title-medium);
      margin: 32px 0 8px;
    }
    h2 mat-icon {
      color: var(--mat-sys-primary);
    }
    .steps li {
      margin-bottom: 12px;
    }
    .server {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin: 8px 0 16px;
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

  /** Nell'app già installata (desktop o Android) la pagina non offre il download. */
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
      // setup e msi sono sempre presenti; android solo se la build Android è riuscita.
      this.release.set(release?.setup?.file && release.msi?.file ? release : null);
    } catch {
      this.release.set(null);
    } finally {
      this.loading.set(false);
    }
  }
}
