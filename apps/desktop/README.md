# App desktop (Tauri 2) — Windows

L'app desktop **incapsula la build web** di `apps/web`: nessun codice duplicato.
Tauri fornisce la finestra nativa (WebView2) e gli installer: **MSI** e setup **.exe** (NSIS).
Scelte e motivazioni: [ADR 0004](../../docs/decisioni/0004-app-desktop.md).

- [Distribuire l'app agli utenti (pagina di download)](#distribuire-lapp-agli-utenti-pagina-di-download)
- [Scaricare l'MSI già pronto dalla CI](#scaricare-lmsi-dalla-ci-senza-installare-nulla)
- [Creare l'MSI sul proprio PC](#creare-lmsi-sul-proprio-pc)
- [Installare e provare l'app](#installare-e-provare-lapp)
- [Problemi frequenti](#problemi-frequenti)
- [Da fare prima della vendita](#da-fare-prima-della-vendita)

## Come funziona

- **Un solo installer per tutti i clienti.** Al primo avvio l'app chiede l'**indirizzo del server**
  dell'azienda (es. `https://civilbeefy.azienda.it`), scarica da lì `config.json` e conserva
  l'ultima copia per avviarsi anche offline. Il server si cambia da *Profilo → Cambia server*.
- **Login**: stesso Keycloak della web, client pubblico `gestionale-desktop`. La pagina di login
  si apre nella finestra dell'app e al termine torna nell'app.
- **API**: l'app gira sull'origine `http://tauri.localhost`; l'API la accetta (CORS).
- La modalità desktop si riconosce da sola (`apps/web/src/app/core/platform.ts`): la stessa build
  web vale per browser e desktop.

```
apps/desktop/
├── package.json             # CLI di Tauri: npm run dev / npm run build
└── src-tauri/
    ├── tauri.conf.json      # finestra, installer (lingua italiana), comandi di build della web
    ├── Cargo.toml, Cargo.lock
    ├── capabilities/        # permessi delle API native (per ora solo core:default)
    ├── icons/               # generate da apps/web/public/icons/icon-512x512.png
    └── src/                 # main.rs, lib.rs
```

## Distribuire l'app agli utenti (pagina di download)

Gli utenti scaricano l'app dal **server della loro azienda**, alla pagina pubblica
**`https://<dominio>/app-desktop`** («App per Windows»), collegata dalla pagina di accesso e dal menu
utente (nascosta dentro l'app desktop). La pagina mostra:

- il pulsante di download del setup `.exe` (installazione per l'utente, senza permessi di amministratore);
- i passi di installazione, compreso l'avviso di SmartScreen finché l'app non è firmata;
- l'**indirizzo del server** da inserire al primo avvio, con il pulsante *Copia*;
- per il reparto IT il pacchetto **MSI** (installazione centralizzata) e gli SHA-256 dei file.

Come ci arrivano gli installer:

1. a ogni merge su `main` il workflow **Release immagini** richiama **CI Desktop** (Windows);
2. `infra/docker/web/prepare-desktop-downloads.sh` copia gli installer in `apps/web/public/downloads/`
   come `civilbeefy-setup.exe` e `civilbeefy.msi`, con `desktop.json` (versione, commit, data,
   dimensioni, SHA-256);
3. finiscono nell'**immagine web** dello stesso commit: con **Deploy** ogni server offre l'app della
   propria versione, e un ritorno indietro riporta anche l'installer precedente.

Se la build desktop fallisce le immagini escono comunque, senza installer: la pagina dice che il
download non è disponibile. Per provarla in locale basta mettere dei file in
`apps/web/public/downloads/` (cartella ignorata da git) con lo script sopra.

## Scaricare l'MSI dalla CI (senza installare nulla)

Le pull request che toccano `apps/web` o `apps/desktop` fanno partire **CI Desktop**, che crea gli
installer su Windows; su `main` li crea **Release immagini** (job `desktop`).

1. GitHub → repository → **Actions** → **Release immagini** (per `main`) o **CI Desktop** (per una PR) → l'esecuzione che interessa.
2. In fondo alla pagina, sezione **Artifacts**: scarica `gestionale-desktop-windows` (zip).
3. Dentro ci sono `civil beefy_<versione>_x64_it-IT.msi` e `civil beefy_<versione>_x64-setup.exe`.

Con GitHub CLI:

```bash
gh run download --repo giorgiorest/gestionale --name gestionale-desktop-windows
```

## Creare l'MSI sul proprio PC

### 1. Prerequisiti (una volta sola)

| Cosa | Come (PowerShell) |
|------|-------------------|
| Microsoft C++ Build Tools, carico «Sviluppo di applicazioni desktop con C++» | `winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"` |
| Rust (toolchain MSVC) | `winget install Rustlang.Rustup`, poi riaprire il terminale e `rustup default stable-msvc` |
| Node.js 22 | `winget install OpenJS.NodeJS.LTS` (o la versione 22 dal sito) |
| WebView2 | già presente su Windows 10 e 11 |
| VBScript (serve a WiX per creare l'MSI) | Impostazioni → Sistema → Funzionalità facoltative → *VBSCRIPT* deve essere installata |

Controllo rapido:

```bash
rustc --version
cargo --version
node --version
```

WiX (per l'MSI) e NSIS (per il setup .exe) **non** vanno installati: Tauri li scarica da solo
alla prima build (serve la connessione a Internet).

### 2. Dipendenze del progetto

Dalla radice del repository, prima la web app (la build desktop la compila) e poi la desktop:

```bash
cd apps/web
npm ci
cd ../desktop
npm ci
```

### 3. Build dell'MSI

Da `apps/desktop`:

```bash
npm run build -- --bundles msi
```

Senza `--bundles msi` vengono creati sia l'MSI sia il setup `.exe`.
La prima build richiede 10–15 minuti (compilazione Rust); le successive sono molto più veloci.

Risultato:

```
apps/desktop/src-tauri/target/release/bundle/msi/civil beefy_0.1.0_x64_it-IT.msi
```

La versione nel nome del file è `version` in `src-tauri/tauri.conf.json`: aumentala a ogni
rilascio, altrimenti Windows non aggiorna un'installazione esistente.

## Installare e provare l'app

1. Doppio clic sull'MSI. L'installer **non è ancora firmato**: Windows SmartScreen mostra
   «Windows ha protetto il PC» → *Ulteriori informazioni* → *Esegui comunque*.
2. Avvia **civil beefy** dal menu Start: compare *Collegati alla tua azienda*.
3. Inserisci l'indirizzo del server e accedi.

### Con lo stack locale (sviluppo)

Servono database, Keycloak, API e web avviati come nel README principale, con due attenzioni:

- **Keycloak deve conoscere il client `gestionale-desktop`.** Se il container di Keycloak è stato
  creato prima dell'app desktop, ricrealo (l'import del realm avviene solo alla creazione):
  da `infra/docker`, `podman-compose up -d --force-recreate keycloak`.
- Come indirizzo del server inserisci `http://localhost:4200` (con `npm start` in apps/web).

Se dopo l'indirizzo l'app segnala «Server non raggiungibile» anche con il server acceso,
WebView2 sta bloccando le richieste verso `localhost` da un'app (protezione *Local Network
Access* delle versioni recenti). Con un server reale (`https://…`) non succede; per lo sviluppo
usa la modalità sviluppo qui sotto.

### Modalità sviluppo (senza installer)

```bash
npm run dev
```

Avvia anche `ng serve` di apps/web e apre la finestra su `http://localhost:4200` con ricarica
automatica. In questa modalità l'app si comporta come la web: niente scelta del server, client
`gestionale-web`, proxy `/api`.

## Problemi frequenti

| Messaggio | Soluzione |
|-----------|-----------|
| `linker 'link.exe' not found` | Mancano i C++ Build Tools (carico «Sviluppo di applicazioni desktop con C++»), oppure il terminale va riaperto dopo l'installazione. |
| `failed to run ...light.exe` / errore WiX | Attiva la funzionalità facoltativa **VBSCRIPT** di Windows e riprova. |
| `npm --prefix ../web run build` fallisce con `ng: not found` | Manca `npm ci` in `apps/web` (passo 2). |
| Download di WiX o NSIS non riuscito | Serve Internet alla prima build; dietro proxy aziendale imposta `HTTPS_PROXY`. |
| L'MSI non sostituisce la versione installata | Aumenta `version` in `src-tauri/tauri.conf.json` prima della build. |
| Dopo il login Keycloak mostra «Invalid parameter: redirect_uri» | Il realm non contiene il client `gestionale-desktop`: ricrea il container di Keycloak. |

## Da fare prima della vendita

Tracciati come issue su GitHub.

- **Firma del codice** ([#3](https://github.com/giorgiorest/gestionale/issues/3)). Senza firma,
  SmartScreen mostra «autore sconosciuto» e molte aziende bloccano l'installazione. Serve un
  certificato di firma del codice (OV/EV, oppure Azure Trusted Signing), configurato in
  `bundle.windows` di `tauri.conf.json` e in CI con il certificato nei GitHub Secrets.
- **Aggiornamenti automatici** ([#4](https://github.com/giorgiorest/gestionale/issues/4)). Plugin
  `tauri-plugin-updater`, aggiornamenti firmati: la chiave privata solo nei GitHub Secrets
  (`TAURI_SIGNING_PRIVATE_KEY`), nell'app solo la chiave pubblica.
- **Versione**: allineare `version` di `tauri.conf.json` ai rilasci (tag Git).
- **Login nel browser di sistema** (pratica raccomandata per le app native, RFC 8252): da valutare.
- **Distribuzione aziendale**: l'MSI si presta all'installazione silenziosa
  (`msiexec /i "civil beefy_x.y.z_x64_it-IT.msi" /qn`) via Intune o criteri di gruppo; in futuro si
  potrà precompilare l'indirizzo del server con una proprietà dell'MSI.
- **macOS e Linux**: rimandati.
