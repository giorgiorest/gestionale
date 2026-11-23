import { Injectable } from '@angular/core';
import { isPackagedDesktopApp } from '../platform';

/** Configurazione dell'installazione, letta a runtime da `config.json`. */
export interface AppConfig {
  /** URL base dell'API (relativo, es. "/api", o assoluto). */
  apiUrl: string;
  auth: {
    /** Issuer OIDC, es. https://login.cliente.it/realms/gestionale */
    authority: string;
    /** Client OIDC pubblico della web app. */
    clientId: string;
    /** Client OIDC pubblico dell'app desktop (default "gestionale-desktop"). */
    desktopClientId?: string;
  };
  /** Personalizzazione dell'installazione (facoltativa): nome, logo e colore principale. */
  branding?: Branding;
}

/** Marchio mostrato nell'app. Senza valori si usa quello di prodotto. */
export interface Branding {
  /** Nome mostrato nella barra e nel titolo della finestra. */
  name?: string;
  /** URL del logo (SVG o PNG), relativo al sito o assoluto. */
  logoUrl?: string;
  /** Colore principale nel formato #RRGGBB. */
  primaryColor?: string;
}

/** Chiave (localStorage) dell'indirizzo del server scelto nell'app desktop. */
export const SERVER_URL_KEY = 'gestionale.serverUrl';
/** Ultima configurazione scaricata dal server, per avviare l'app desktop anche offline. */
const CACHED_CONFIG_KEY = 'gestionale.config';

/**
 * Carica la configurazione all'avvio dell'app (prima di autenticazione e routing).
 *
 * - **Web/PWA**: `config.json` dello stesso sito (in sviluppo `public/config.json`;
 *   nell'immagine Docker generato dalle variabili d'ambiente).
 * - **App desktop**: un solo installer per tutti i clienti. Al primo avvio l'utente indica
 *   l'indirizzo del server della sua azienda (pagina /server); l'app scarica
 *   `<server>/config.json`, rende assoluti gli URL e conserva l'ultima copia per l'avvio offline.
 */
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private value: AppConfig | null = null;

  /** true nell'app desktop installata. */
  readonly desktop = isPackagedDesktopApp();
  /** Ultimo errore di caricamento (solo app desktop). */
  lastError: string | null = null;

  get config(): AppConfig {
    if (!this.value) {
      throw new Error('Configurazione non ancora caricata');
    }
    return this.value;
  }

  get hasConfig(): boolean {
    return this.value !== null;
  }

  /** Indirizzo del server scelto nell'app desktop. */
  get serverUrl(): string | null {
    return readStorage(SERVER_URL_KEY);
  }

  async load(): Promise<AppConfig | null> {
    if (!this.desktop) {
      this.value = await fetchConfig('config.json');
      return this.value;
    }
    const server = this.serverUrl;
    if (!server) {
      return null;
    }
    try {
      this.value = this.forDesktop(await fetchConfig(new URL('config.json', withSlash(server)).href), server);
      writeStorage(CACHED_CONFIG_KEY, JSON.stringify(this.value));
    } catch (error) {
      this.lastError = `Server ${server} non raggiungibile`;
      const cached = readStorage(CACHED_CONFIG_KEY);
      this.value = cached ? (JSON.parse(cached) as AppConfig) : null;
    }
    return this.value;
  }

  /** App desktop: verifica il server indicato e lo memorizza. Lancia un errore se non risponde. */
  async connect(serverUrl: string): Promise<void> {
    const server = normalizeServerUrl(serverUrl);
    const config = this.forDesktop(await fetchConfig(new URL('config.json', withSlash(server)).href), server);
    writeStorage(SERVER_URL_KEY, server);
    writeStorage(CACHED_CONFIG_KEY, JSON.stringify(config));
  }

  /** App desktop: dimentica il server (per collegarsi a un'altra azienda). */
  disconnect(): void {
    removeStorage(SERVER_URL_KEY);
    removeStorage(CACHED_CONFIG_KEY);
  }

  /** Imposta la configurazione direttamente (test). */
  set(config: AppConfig): void {
    this.value = config;
  }

  private forDesktop(config: AppConfig, server: string): AppConfig {
    const base = withSlash(server);
    return {
      apiUrl: new URL(config.apiUrl, base).href.replace(/\/$/, ''),
      auth: {
        authority: new URL(config.auth.authority, base).href.replace(/\/$/, ''),
        clientId: config.auth.desktopClientId ?? 'gestionale-desktop',
        desktopClientId: config.auth.desktopClientId,
      },
      branding: config.branding && {
        ...config.branding,
        logoUrl: config.branding.logoUrl ? new URL(config.branding.logoUrl, base).href : undefined,
      },
    };
  }
}

/** Normalizza l'indirizzo inserito dall'utente: aggiunge https:// se manca, toglie la barra finale. */
export function normalizeServerUrl(input: string): string {
  const trimmed = input.trim();
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  const url = new URL(withScheme);
  return `${url.origin}${url.pathname}`.replace(/\/$/, '');
}

async function fetchConfig(url: string): Promise<AppConfig> {
  const response = await fetch(url, { cache: 'no-cache' });
  if (!response.ok) {
    throw new Error(`Impossibile leggere ${url}: HTTP ${response.status}`);
  }
  const config = (await response.json()) as AppConfig;
  if (!config?.apiUrl || !config?.auth?.authority) {
    throw new Error(`${url} non è una configurazione del gestionale`);
  }
  return config;
}

function withSlash(url: string): string {
  return url.endsWith('/') ? url : `${url}/`;
}

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage non disponibile: la configurazione resta valida solo per questa sessione.
  }
}

function removeStorage(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    // niente da fare
  }
}
