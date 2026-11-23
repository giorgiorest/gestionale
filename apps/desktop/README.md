# App desktop (Tauri 2)

L'app desktop **incapsula la build web** di `apps/web`: nessun codice duplicato.
Tauri fornisce la finestra nativa (WebView2 su Windows) e il pacchetto di installazione.
Scelte e motivazioni: [ADR 0004](../../docs/decisioni/0004-app-desktop.md).

```
apps/desktop/
├── package.json             # CLI di Tauri (npm run dev / npm run build)
└── src-tauri/
    ├── tauri.conf.json      # finestra, bundle, comandi di build della web
    ├── Cargo.toml, Cargo.lock
    ├── capabilities/        # permessi delle API native (per ora solo core:default)
    ├── icons/               # generate da apps/web/public/icons/icon-512x512.png
    └── src/                 # main.rs, lib.rs
```

## Come funziona

- **Un solo installer per tutti i clienti.** Al primo avvio l'app chiede l'**indirizzo del server**
  dell'azienda (es. `https://gestionale.azienda.it`), scarica da lì `config.json` e conserva
  l'ultima copia per avviarsi anche offline. Il server si cambia da *Profilo → Cambia server*.
- **Login**: stesso Keycloak della web, client pubblico `gestionale-desktop` (Authorization Code
  + PKCE). La pagina di login si apre dentro la finestra dell'app e al termine torna a
  `http://tauri.localhost/auth/callback`.
- **API**: l'app gira sull'origine `http://tauri.localhost` (Windows), quindi chiama l'API con
  l'indirizzo completo; l'API accetta quell'origine (CORS, `CORS_ALLOWED_ORIGINS`).
- Il riconoscimento della modalità desktop è automatico (`core/platform.ts` in apps/web):
  la stessa build web vale per browser e desktop.

## Prerequisiti (Windows)

1. **Microsoft C++ Build Tools** con il carico di lavoro «Sviluppo di applicazioni desktop con C++».
2. **WebView2**: già presente su Windows 10 e 11 (l'installer lo scarica se manca).
3. **Rust** da <https://rustup.rs> (toolchain predefinita, MSVC).
4. Node.js 22.

## Sviluppo

Con API, Keycloak e database avviati come da README principale, da `apps/desktop`:

```bash
npm install
npm run dev
```

`npm run dev` avvia anche `ng serve` di apps/web e apre la finestra su `http://localhost:4200`:
in questa modalità l'app si comporta come la web (niente scelta del server, client
`gestionale-web`, proxy `/api`), con ricarica automatica.

## Installer

```bash
npm run build
```

Risultato in `src-tauri/target/release/bundle/`:

- `msi/Gestionale_<versione>_x64_it-IT.msi` (o `en-US`)
- `nsis/Gestionale_<versione>_x64-setup.exe`

La CI (`.github/workflows/ci-desktop.yml`) genera gli stessi file su Windows e li pubblica come
artifact `gestionale-desktop-windows`.

### Provare l'installer con lo stack locale

Al primo avvio inserisci come server `http://localhost:4200` (con `npm start` in apps/web).
Nota: le versioni recenti di WebView2 possono bloccare le richieste da `tauri.localhost` verso
indirizzi `localhost` (protezione *Local Network Access*). Con un server reale (`https://…`)
il problema non si pone; in sviluppo usa `npm run dev`.

## Da fare prima della vendita

Tracciati come issue su GitHub.

- **Firma del codice (Windows).** Senza firma, SmartScreen mostra «autore sconosciuto» e alcune
  aziende bloccano l'installazione. Serve un certificato di firma del codice (OV/EV, oppure
  Azure Trusted Signing); la firma si configura in `bundle.windows` di `tauri.conf.json` e in CI,
  con il certificato nei GitHub Secrets. Il certificato non va mai nel repository.
- **Aggiornamenti automatici.** Plugin `tauri-plugin-updater`: l'app controlla un manifest degli
  aggiornamenti (es. GitHub Releases o il server del cliente) e installa le nuove versioni,
  verificandone la firma. La chiave privata degli aggiornamenti sta solo nei GitHub Secrets
  (`TAURI_SIGNING_PRIVATE_KEY`); nell'app va solo la chiave pubblica.
- **Versione**: allineare `version` di `tauri.conf.json` ai rilasci (tag Git).
- **Login nel browser di sistema** (pratica raccomandata per le app native, RFC 8252): da valutare
  al posto del login nella finestra dell'app.
- **macOS e Linux**: rimandati (servono runner macOS e la notarizzazione Apple).
