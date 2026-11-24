import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BrandingService } from '../../core/branding/branding.service';
import { AppConfigService } from '../../core/config/app-config';

/**
 * App desktop: indirizzo del server dell'azienda (primo avvio o cambio server).
 * Un solo installer serve tutti i clienti: la configurazione arriva dal server indicato.
 */
@Component({
  selector: 'app-server',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="centered">
      <mat-card appearance="outlined" class="card">
        <mat-card-content>
          <img [src]="branding.logoUrl()" alt="" class="logo" />
          <h1>Collegati al server</h1>
          <p class="muted">
            Inserisci l'indirizzo che apri nel browser per usare {{ branding.name() }} (lo trovi
            anche nella pagina «App per Windows» del sito). Poi accedi con il tuo utente: l'azienda
            è quella del tuo account.
          </p>
          <form (ngSubmit)="connect()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Indirizzo del server</mat-label>
              <input
                matInput
                name="url"
                placeholder="es. gestionale.miaazienda.it"
                [(ngModel)]="url"
              />
              <mat-hint>Basta il nome del sito: https:// si aggiunge da solo</mat-hint>
            </mat-form-field>
            <button
              mat-flat-button
              type="submit"
              class="full-width"
              [disabled]="!url.trim() || busy()"
            >
              Connetti
            </button>
          </form>
          @if (error()) {
            <div class="banner error" role="alert" data-testid="server-error">{{ error() }}</div>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .card {
      width: 100%;
      max-width: 440px;
      padding: 16px 8px;
    }
    .logo {
      height: 48px;
    }
    h1 {
      font: var(--mat-sys-headline-small);
      margin: 16px 0 4px;
    }
    .banner {
      margin-top: 16px;
    }
  `,
})
export class ServerPage {
  private readonly config = inject(AppConfigService);
  readonly branding = inject(BrandingService);
  readonly busy = signal(false);
  readonly error = signal<string | null>(this.config.lastError);
  url = this.config.serverUrl ?? '';

  async connect(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.config.connect(this.url);
      // Riavvio completo: configurazione e sessione si inizializzano con il nuovo server.
      window.location.replace(new URL('./', document.baseURI).href);
    } catch {
      this.error.set('Server non raggiungibile o indirizzo non valido. Controlla e riprova.');
      this.busy.set(false);
    }
  }
}
