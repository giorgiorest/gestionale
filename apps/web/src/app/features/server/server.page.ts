import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IonButton, IonContent, IonHeader, IonInput, IonItem, IonList, IonNote, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { AppConfigService } from '../../core/config/app-config';

/**
 * App desktop: indirizzo del server dell'azienda (primo avvio o cambio server).
 * Un solo installer serve tutti i clienti: la configurazione arriva dal server indicato.
 */
@Component({
  selector: 'app-server',
  imports: [FormsModule, IonButton, IonContent, IonHeader, IonInput, IonItem, IonList, IonNote, IonText, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Gestionale</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <form class="server" (ngSubmit)="connect()">
        <ion-text><h1>Collegati alla tua azienda</h1></ion-text>
        <p>Inserisci l'indirizzo del gestionale che ti ha comunicato l'amministratore.</p>
        <ion-list>
          <ion-item>
            <ion-input
              label="Indirizzo del server"
              labelPlacement="stacked"
              name="url"
              placeholder="https://gestionale.azienda.it"
              [(ngModel)]="url"
            ></ion-input>
          </ion-item>
        </ion-list>
        <ion-button type="submit" expand="block" [disabled]="!url.trim() || busy()">Connetti</ion-button>
        @if (error()) {
          <ion-note color="danger" data-testid="server-error">{{ error() }}</ion-note>
        }
      </form>
    </ion-content>
  `,
  styles: `
    .server {
      max-width: 420px;
      margin: 12vh auto 0;
    }
  `,
})
export class ServerPage {
  private readonly config = inject(AppConfigService);
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
