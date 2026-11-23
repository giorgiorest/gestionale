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
| `public/branding/logo.svg` | logo di prodotto (provvisorio) |
| `public/config.json` | configurazione di sviluppo (API, Keycloak, marchio) |

## Cambiare logo e colori del prodotto

1. **Logo**: sostituisci `public/branding/logo.svg` (meglio SVG quadrato; va bene anche PNG,
   aggiornando `DEFAULT_BRANDING.logoUrl` in `src/app/core/branding/branding.service.ts`).
2. **Colori**: il colore principale attuale è `#0B5C8E`. Per cambiarlo, rigenera la palette e
   aggiorna `$brand-primary` in `src/styles.scss`:

   ```bash
   npx ng generate @angular/material:theme-color --primary-color=#0B5C8E --directory=src/styles/ --is-scss --force --interactive=false
   ```

   Si possono indicare anche `--secondary-color`, `--tertiary-color`, `--neutral-color`.
3. **Icone dell'app** (PWA e desktop) dal logo definitivo:
   - PWA: `public/icons/` e `public/favicon.ico`;
   - desktop: da `apps/desktop`, `npx tauri icon ../web/public/icons/icon-512x512.png -o src-tauri/icons`.

## Marchio di un cliente (senza ricompilare)

In `config.json` dell'installazione:

```json
"branding": {
  "name": "Rossi Gestionale",
  "logoUrl": "https://www.rossi.example/logo.svg",
  "primaryColor": "#8B1F3A"
}
```

Con Docker le stesse impostazioni arrivano da `BRAND_NAME`, `BRAND_LOGO_URL`, `BRAND_PRIMARY_COLOR`.

## Regole per i componenti

- Componenti di Angular Material, importati per modulo (`@angular/material/button`, …).
- Colori, forme e tipografia solo dalle variabili di sistema: `var(--mat-sys-primary)`,
  `var(--mat-sys-surface-container-high)`, `var(--mat-sys-title-medium)`… mai colori fissi,
  così il marchio del cliente si applica ovunque.
- Pagine dentro `<div class="page">` con `<div class="page-header"><h1>…</h1></div>`.
- Icone: `<mat-icon>nome</mat-icon>` con i nomi di [Material Symbols](https://fonts.google.com/icons)
  (il font è incluso nell'app).
