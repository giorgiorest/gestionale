import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { BrandingService } from '../../core/branding/branding.service';
import { ConfigurazioneAppService } from '../../core/configurazione/configurazione-app';

/** Cookie e archiviazioni usati dall'applicazione: tutti tecnici, nessuno di profilazione. */
interface StorageItem {
  name: string;
  who?: string;
  purpose: string;
  duration: string;
}

/**
 * Informativa su cookie e dati salvati sul dispositivo (uguale per ogni installazione),
 * con i link ai documenti del titolare configurati in config.json (`legal`).
 * Va aggiornata insieme a docs/privacy-gdpr.md quando si aggiunge un cookie o un'archiviazione.
 */
@Component({
  selector: 'app-privacy',
  imports: [MatButtonModule, MatCardModule, MatIconModule, RouterLink],
  template: `
    <div class="page narrow">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Privacy e cookie</mat-card-title>
          <mat-card-subtitle>{{ branding.name() }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <h2>In breve</h2>
          <p>
            {{ branding.name() }} usa solo <strong>cookie tecnici</strong> e archiviazioni del
            browser necessari all'accesso e al funzionamento anche senza connessione. Non usa cookie
            di profilazione, strumenti di statistica o pubblicità, né contenuti di terze parti:
            caratteri e icone sono inclusi nell'applicazione. Per questo non è richiesto il consenso
            (provvedimento del Garante privacy del 10 giugno 2021).
          </p>

          <h2>Cookie</h2>
          <ul class="items">
            @for (item of cookies; track item.name) {
              <li>
                <strong>{{ item.name }}</strong><span class="muted"
                  >&nbsp;· {{ item.who }} · {{ item.duration }}</span
                >
                <div>{{ item.purpose }}</div>
              </li>
            }
          </ul>

          <h2>Dati salvati sul dispositivo</h2>
          <ul class="items">
            @for (item of storage; track item.name) {
              <li>
                <strong>{{ item.name }}</strong><span class="muted"
                  >&nbsp;· {{ item.duration }}</span
                >
                <div>{{ item.purpose }}</div>
              </li>
            }
          </ul>

          <h2>Trattamento dei dati personali</h2>
          <p>
            I dati inseriti nell'applicazione sono trattati per conto dell'azienda che ti ha dato
            l'accesso, che ne è titolare.
            @if (legal()?.privacyUrl) {
              I dettagli sono nell'<a [href]="legal()!.privacyUrl" target="_blank" rel="noopener"
                >informativa privacy</a
              >.
            } @else {
              Per l'informativa sul trattamento e per esercitare i tuoi diritti rivolgiti alla tua
              azienda.
            }
          </p>
          @if (legal()?.termsUrl) {
            <p>
              <a [href]="legal()!.termsUrl" target="_blank" rel="noopener"
                >Condizioni d'uso del servizio</a
              >
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
    h2 {
      font: var(--mat-sys-title-medium);
      margin: 24px 0 8px;
    }
    .items {
      padding-left: 20px;
    }
    .items li {
      margin-bottom: 12px;
    }
    a {
      color: var(--mat-sys-primary);
    }
  `,
})
export class PrivacyPage {
  readonly branding = inject(BrandingService);
  private readonly appConfig = inject(ConfigurazioneAppService);

  readonly legal = computed(() =>
    this.appConfig.hasConfig ? this.appConfig.config.legal : undefined,
  );

  readonly cookies: StorageItem[] = [
    {
      name: 'AUTH_SESSION_ID, KC_RESTART',
      who: 'servizio di accesso',
      purpose:
        'Completano la procedura di accesso in corso (sicurezza e protezione da richieste contraffatte).',
      duration: 'durante l’accesso',
    },
    {
      name: 'KEYCLOAK_IDENTITY, KEYCLOAK_SESSION',
      who: 'servizio di accesso',
      purpose:
        'Mantengono la sessione di accesso, così non devi reinserire la password a ogni pagina.',
      duration: 'fino all’uscita o alla scadenza della sessione (al massimo 10 ore)',
    },
    {
      name: 'KEYCLOAK_REMEMBER_ME',
      who: 'servizio di accesso',
      purpose: 'Ricorda il nome utente, solo se scegli «Ricordami» nella pagina di accesso.',
      duration: 'fino a quando non lo togli',
    },
    {
      name: 'KEYCLOAK_LOCALE',
      who: 'servizio di accesso',
      purpose: 'Ricorda la lingua scelta nella pagina di accesso.',
      duration: 'sessione',
    },
  ];

  readonly storage: StorageItem[] = [
    {
      name: 'Sessione dell’applicazione (sessionStorage)',
      purpose: 'Token di accesso alle funzioni dell’applicazione.',
      duration: 'fino alla chiusura della scheda o all’uscita',
    },
    {
      name: 'Dati per l’uso offline (IndexedDB)',
      purpose:
        'Copia dei dati che consulti (es. clienti e notifiche) e modifiche fatte senza connessione, in attesa di invio. ' +
        'Appartengono solo al tuo utente: vengono cancellati quando esci o se sullo stesso browser accede un altro utente.',
      duration: 'fino all’uscita',
    },
    {
      name: 'File dell’applicazione (cache del browser)',
      purpose:
        'Permettono di aprire l’applicazione anche senza connessione. Non contengono dati personali.',
      duration: 'fino al successivo aggiornamento',
    },
    {
      name: 'Indirizzo del server (localStorage, solo app desktop)',
      purpose: 'Server della tua azienda scelto al primo avvio.',
      duration: 'fino a «Cambia server»',
    },
  ];
}
