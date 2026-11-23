# Web app (Angular 22 + Angular Material)

Interfaccia unica per browser (PWA) e app desktop (Tauri). Avvio, build e test: vedi il
[README principale](../../README.md). Scelte grafiche: [ADR 0006](../../docs/decisioni/0006-interfaccia-angular-material.md).

## Struttura

| Cartella | Contenuto |
|----------|-----------|
| `src/app/core/layout` | layout principale: barra superiore e menu laterale |
| `src/app/core/branding` | marchio dell'installazione (nome, logo, colore) da `config.json` |
| `src/app/core/…` | configurazione, autenticazione, profilo, entitlement, notifiche, sincronizzazione |
| `src/app/features/…` | una cartella per funzione (clienti, notifiche, comunicazioni, …) |
| `src/styles.scss` | tema Material 3, stili comuni delle pagine (`.page`, `.page-header`, `.banner`, `.status`) |
| `src/styles/_theme-colors.scss` | palette generata dal colore del marchio |
| `public/branding/logo.svg` | simbolo del marchio (celle esagonali), usato nella barra e come icona |
| `public/config.json` | configurazione di sviluppo (API, Keycloak, marchio) |

## Cambiare logo e colori del prodotto

1. **Logo**: il simbolo è `public/branding/logo.svg` (ridisegnato in SVG dal marchio; sostituirlo
   con l'esportazione originale quando disponibile). Variante per sfondi scuri:
   `public/branding/logo-chiaro.svg`.
2. **Colori**: principale `#0B5C8E` (blu) e di accento `#F2B300` (giallo del logo). Per cambiarli,
   rigenera la palette e aggiorna `$brand-primary` / `$brand-accent` in `src/styles.scss`:

   ```bash
   npx ng generate @angular/material:theme-color --primary-color=#0B5C8E --tertiary-color=#F2B300 --directory=src/styles/ --is-scss --force --interactive=false
   ```

   L'accento si usa con `var(--mat-sys-tertiary)` e il testo sopra con `var(--mat-sys-on-tertiary)`
   (scuro): linea sotto la barra superiore, contatori delle notifiche. Mai come colore del testo
   su sfondo chiaro, perché non sarebbe leggibile.

   Si possono indicare anche `--secondary-color`, `--tertiary-color`, `--neutral-color`.
3. **Icone dell'app** (PWA e desktop), da rigenerare quando cambia il logo:
   - si parte da un PNG 1024×1024 del simbolo con sfondo trasparente e un piccolo margine;
   - desktop: da `apps/desktop`, `npx tauri icon <png-1024> -o src-tauri/icons`
     (poi eliminare le cartelle `android` e `ios` generate, il mobile usa Capacitor);
   - favicon: copiare `apps/desktop/src-tauri/icons/icon.ico` in `public/favicon.ico`;
   - PWA: `public/icons/icon-NNxNN.png` nelle dimensioni del manifest.

## Marchio di un cliente (senza ricompilare)

In `config.json` dell'installazione:

```json
"branding": {
  "name": "Rossi Gestionale",
  "logoUrl": "https://www.rossi.example/logo.svg",
  "primaryColor": "#8B1F3A",
  "accentColor": "#E0A100"
}
```

Con Docker le stesse impostazioni arrivano da `BRAND_NAME`, `BRAND_LOGO_URL`, `BRAND_PRIMARY_COLOR`,
`BRAND_ACCENT_COLOR`.

Il logo conviene ospitarlo sullo stesso sito: un logo su un altro dominio fa arrivare a quel dominio
l'indirizzo IP di chi apre l'app (vedi [docs/privacy-gdpr.md](../../docs/privacy-gdpr.md)).

## Service worker (PWA)

`ngsw-config.json` → `navigationUrls`: il service worker risponde con la web app (anche offline)
alle navigazioni dell'utente, **tranne** `/sso/**` (Keycloak, sullo stesso dominio in produzione),
`/api/**` e `/downloads/**`. Senza queste esclusioni il clic su «Accedi» tornava alla pagina di
accesso: il service worker serviva la web app al posto della pagina di login di Keycloak. Un nuovo
percorso servito da un altro servizio dietro lo stesso dominio va aggiunto qui.

Un aggiornamento arriva ai browser che hanno già l'app quando il service worker, a pagina aperta e
inattiva per qualche secondo, scarica la nuova versione: vale dalla successiva apertura.

## Privacy e dati sul dispositivo

- Pagina pubblica **/privacy** («Privacy e cookie»): elenco dei cookie tecnici e dei dati salvati nel
  browser, con i link ai documenti del titolare da `config.json`:

  ```json
  "legal": {
    "privacyUrl": "https://www.rossi.example/privacy",
    "termsUrl": "https://www.rossi.example/condizioni"
  }
  ```

  Con Docker: `LEGAL_PRIVACY_URL` e `LEGAL_TERMS_URL`. Chi aggiunge un cookie o un'archiviazione nel
  browser aggiorna questa pagina (`features/privacy`) e `docs/privacy-gdpr.md`.
- **Dati offline legati all'utente**: il database locale (IndexedDB) registra a quale `tenant_id:sub`
  appartiene (`LocalDataService`). All'avvio, se ha effettuato l'accesso un altro utente, viene
  svuotato prima di mostrare qualsiasi pagina.
- **Uscita** (`LogoutService`): invia le modifiche in attesa, chiede conferma se ne restano (offline),
  poi cancella i dati locali e chiude la sessione. Nell'app desktop anche «Cambia server» li cancella.

## Regole per i componenti

- Componenti di Angular Material, importati per modulo (`@angular/material/button`, …).
- Colori, forme e tipografia solo dalle variabili di sistema: `var(--mat-sys-primary)`,
  `var(--mat-sys-surface-container-high)`, `var(--mat-sys-title-medium)`… mai colori fissi,
  così il marchio del cliente si applica ovunque.
- Pagine dentro `<div class="page">` con `<div class="page-header"><h1>…</h1></div>`.
- Icone: `<mat-icon>nome</mat-icon>` con i nomi di [Material Symbols](https://fonts.google.com/icons)
  (il font è incluso nell'app).
