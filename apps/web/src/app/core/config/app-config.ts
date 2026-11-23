import { Injectable } from '@angular/core';

/** Configurazione dell'installazione, letta a runtime da `config.json`. */
export interface AppConfig {
  /** URL base dell'API (relativo, es. "/api", o assoluto). */
  apiUrl: string;
  auth: {
    /** Issuer OIDC, es. https://login.cliente.it/realms/gestionale */
    authority: string;
    /** Client OIDC pubblico registrato nel provider. */
    clientId: string;
  };
}

/**
 * Carica `config.json` all'avvio dell'app (prima di autenticazione e routing).
 * In sviluppo il file è `public/config.json`; nell'immagine Docker viene generato
 * dalle variabili d'ambiente del container (vedi infra/docker/web).
 */
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private value: AppConfig | null = null;

  get config(): AppConfig {
    if (!this.value) {
      throw new Error('Configurazione non ancora caricata');
    }
    return this.value;
  }

  async load(url = 'config.json'): Promise<AppConfig> {
    const response = await fetch(url, { cache: 'no-cache' });
    if (!response.ok) {
      throw new Error(`Impossibile leggere ${url}: HTTP ${response.status}`);
    }
    this.value = (await response.json()) as AppConfig;
    return this.value;
  }

  /** Imposta la configurazione direttamente (test). */
  set(config: AppConfig): void {
    this.value = config;
  }
}
