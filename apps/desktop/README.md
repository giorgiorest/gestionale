# App impacchettata (Tauri 2) — Windows e Android

L'app **incapsula la build web** di `apps/web`: nessun codice duplicato.
Tauri fornisce la finestra nativa e i pacchetti: su Windows **MSI** e setup **.exe** (NSIS,
WebView2), su Android un **APK** firmato (WebView di sistema).
Scelte e motivazioni: [ADR 0004](../../docs/decisioni/0004-app-desktop.md) (desktop) e
[ADR 0011](../../docs/decisioni/0011-app-android.md) (Android).

- [Distribuire l'app agli utenti (pagina di download)](#distribuire-lapp-agli-utenti-pagina-di-download)
- [Scaricare l'MSI già pronto dalla CI](#scaricare-lmsi-dalla-ci-senza-installare-nulla)
- [Creare l'MSI sul proprio PC](#creare-lmsi-sul-proprio-pc)
- [Installare e provare l'app](#installare-e-provare-lapp)
- [APK Android](#apk-android)
- [Problemi frequenti](#problemi-frequenti)
- [Da fare prima della vendita](#da-fare-prima-della-vendita)

## Come funziona

- **Un solo installer per tutti i clienti.** Al primo avvio l'app chiede l'**indirizzo del server**
  dell'azienda (es. `https://civilbeefy.azienda.it`), scarica da lì `config.json` e conserva
  l'ultima copia per avviarsi anche offline. Il server si cambia da *Profilo → Cambia server*.
- **Login**: stesso Keycloak della web, client pubblico `gestionale-desktop`. La pagina di login
  si apre nella finestra dell'app e al termine torna nell'app.
- **API**: l'app gira sull'origine `http://tauri.localhost`; l'API la accetta (CORS).
- La modalità «app impacchettata» si riconosce da sola (`apps/web/src/app/core/platform.ts`):
  la stessa build web vale per browser, desktop e Android. Su Android valgono quindi le stesse
  regole del desktop: scelta del server al primo avvio, client `gestionale-desktop`, niente
  service worker.

```
apps/desktop/
├── package.json                 # CLI di Tauri: npm run dev / npm run build
└── src-tauri/
    ├── tauri.conf.json          # finestra, installer (lingua italiana), comandi di build della web
    ├── tauri.android.conf.json  # solo Android: finestra a tutto schermo (unito al file sopra)
    ├── Cargo.toml, Cargo.lock
    ├── capabilities/            # permessi delle API native (per ora solo core:default)
    ├── icons/                   # generate da apps/web/public/icons/icon-512x512.png
    ├── gen/android/             # progetto Android generato da Tauri (versionato: contiene la firma)
    └── src/                     # main.rs (solo desktop), lib.rs (comune, con mobile_entry_point)
```

## Distribuire l'app agli utenti (pagina di download)

Gli utenti scaricano l'app dal **server della loro azienda**, alla pagina pubblica
**`https://<dominio>/app-desktop`** («App per Windows e Android»), collegata dalla pagina di accesso
e dal menu utente (nascosta dentro l'app già installata). La pagina mostra:

- l'**indirizzo del server** da inserire al primo avvio, con il pulsante *Copia*: è lo stesso per
  entrambe le piattaforme;
- **Windows**: il setup `.exe` (installazione per l'utente, senza permessi di amministratore), i
  passi di installazione compreso l'avviso di SmartScreen finché l'app non è firmata, e per il
  reparto IT il pacchetto **MSI** (installazione centralizzata);
- **Android**: l'**APK** firmato, con il passaggio del consenso a installare «da questa origine»
  (l'app non passa dal Play Store);
- gli SHA-256 di tutti i file.

Come ci arrivano i pacchetti:

1. a ogni merge su `main` il workflow **Release immagini** richiama **CI Desktop** (Windows) e
   **CI Android** (Linux);
2. `infra/docker/web/prepare-desktop-downloads.sh` copia i pacchetti in `apps/web/public/downloads/`
   come `civilbeefy-setup.exe`, `civilbeefy.msi` e `civilbeefy.apk`, con `desktop.json` (versione,
   commit, data, dimensioni, SHA-256);
3. finiscono nell'**immagine web** dello stesso commit: con **Deploy** ogni server offre l'app della
   propria versione, e un ritorno indietro riporta anche i pacchetti precedenti.

Se la build desktop fallisce le immagini escono comunque, senza pacchetti: la pagina dice che il
download non è disponibile. Se fallisce la sola build Android la pagina esce con il solo Windows.
Per provarla in locale basta mettere dei file in `apps/web/public/downloads/` (cartella ignorata
da git) con lo script sopra, che accetta la cartella dell'APK come quarto argomento (facoltativo).

> L'APK è servito da nginx con il tipo `application/vnd.android.package-archive`: senza quello
> Android non riconosce il file scaricato come app da installare.

## Scaricare l'MSI dalla CI (senza installare nulla)

Le pull request che toccano `apps/web` o `apps/desktop` fanno partire **CI Desktop**, che crea gli
installer su Windows; su `main` li crea **Release immagini** (job `desktop`).

1. GitHub → repository → **Actions** → **Release immagini** (per `main`) o **CI Desktop** (per una PR) → l'esecuzione che interessa.
2. In fondo alla pagina, sezione **Artifacts**: scarica `gestionale-desktop-windows` (zip).
3. Dentro ci sono `Civil Beefy_<versione>_x64_it-IT.msi` e `Civil Beefy_<versione>_x64-setup.exe`.

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
apps/desktop/src-tauri/target/release/bundle/msi/Civil Beefy_0.1.0_x64_it-IT.msi
```

La versione nel nome del file è `version` in `src-tauri/tauri.conf.json`: aumentala a ogni
rilascio, altrimenti Windows non aggiorna un'installazione esistente.

## Installare e provare l'app

1. Doppio clic sull'MSI. L'installer **non è ancora firmato**: Windows SmartScreen mostra
   «Windows ha protetto il PC» → *Ulteriori informazioni* → *Esegui comunque*.
2. Avvia **Civil Beefy** dal menu Start: compare *Collegati alla tua azienda*.
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

## APK Android

L'APK contiene la stessa build di `apps/web`. Il progetto Android generato da Tauri sta in
`src-tauri/gen/android/`: è **versionato** perché contiene la configurazione di firma, quindi
non va rigenerato con `tauri android init` senza rimettere quelle modifiche.

### Scaricare l'APK dalla CI

Il workflow **CI Android** (`.github/workflows/android.yml`) gira sulle pull request che toccano
`apps/web` o `apps/desktop` e a mano (*Actions → CI Android → Run workflow*); su `main` lo richiama
**Release immagini**. Pubblica l'artifact `gestionale-android-apk`.

```bash
gh run download --repo giorgiorest/gestionale --name gestionale-android-apk
```

Gli utenti però non passano da qui: scaricano l'APK dalla pagina `/app-desktop` del loro server,
come gli installer Windows (sezione «Distribuire l'app agli utenti»).

### Chiave di firma (una volta sola)

Android installa solo APK firmati, e **la chiave non si può cambiare**: un APK firmato con
un'altra chiave è un'app diversa e non aggiorna quella installata. La chiave va conservata
(gestore di password o cassaforte aziendale), **fuori dal repository**.

```bash
keytool -genkey -v -keystore release.keystore -alias app-release -keyalg RSA -keysize 2048 -validity 10000
```

Poi si caricano i quattro valori nei **GitHub Secrets** del repository
(*Settings → Secrets and variables → Actions*), mai nel codice:

| Secret | Valore |
|--------|--------|
| `ANDROID_KEYSTORE_BASE64` | la keystore codificata: `base64 -w0 release.keystore` (su macOS `base64 -i release.keystore`) |
| `ANDROID_KEYSTORE_PASSWORD` | password della keystore |
| `ANDROID_KEY_ALIAS` | alias della chiave (`app-release` nel comando sopra) |
| `ANDROID_KEY_PASSWORD` | password della chiave |

Si impostano dall'interfaccia di GitHub, oppure con `gh secret set ANDROID_KEY_ALIAS` (il valore
si digita, non si scrive nel comando: finirebbe nella cronologia della shell).

### Compilare l'APK sul proprio PC

Serve l'SDK Android (Android Studio o i *command line tools*) con un NDK, e i target Rust:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

Variabili d'ambiente richieste da Tauri: `ANDROID_HOME` (cartella dell'SDK), `NDK_HOME`
(cartella della versione di NDK, es. `$ANDROID_HOME/ndk/27.2.12479018`) e un JDK 21.

Per la build di rilascio serve `src-tauri/gen/android/key.properties`, che **non sta nel
repository** (lo esclude `.gitignore`):

```properties
storeFile=/percorso/assoluto/release.keystore
storePassword=…
keyAlias=app-release
keyPassword=…
```

`storeFile` è assoluto oppure relativo a `src-tauri/gen/android/`. Senza questo file la build di
rilascio si ferma con un errore: è voluto, un APK non firmato non si installa.

Da `apps/desktop`:

```bash
npm run tauri -- android build --apk
```

Risultato: `src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk`.

### Provare l'app durante lo sviluppo

Con un emulatore acceso o un telefono collegato in debug USB, sempre da `apps/desktop`:

```bash
npm run tauri -- android dev
```

Come `npm run dev` sul desktop, carica `http://localhost:4200` con ricarica automatica: l'app si
comporta come la web (client `gestionale-web`, proxy `/api`). Le build di debug usano la chiave di
debug di Android, quindi `key.properties` non serve.

## Problemi frequenti

| Messaggio | Soluzione |
|-----------|-----------|
| `linker 'link.exe' not found` | Mancano i C++ Build Tools (carico «Sviluppo di applicazioni desktop con C++»), oppure il terminale va riaperto dopo l'installazione. |
| `failed to run ...light.exe` / errore WiX | Attiva la funzionalità facoltativa **VBSCRIPT** di Windows e riprova. |
| `npm --prefix ../web run build` fallisce con `ng: not found` | Manca `npm ci` in `apps/web` (passo 2). |
| Download di WiX o NSIS non riuscito | Serve Internet alla prima build; dietro proxy aziendale imposta `HTTPS_PROXY`. |
| L'MSI non sostituisce la versione installata | Aumenta `version` in `src-tauri/tauri.conf.json` prima della build. |
| Dopo il login Keycloak mostra «Invalid parameter: redirect_uri» | Il realm non contiene il client `gestionale-desktop`: ricrea il container di Keycloak. |
| Android: `Android SDK not found` o `NDK not found` | Mancano `ANDROID_HOME` o `NDK_HOME`. `NDK_HOME` indica la cartella di **una versione** del NDK, non `$ANDROID_HOME/ndk`. |
| Android: `Firma Android non configurata: manca …/key.properties` | Build di rilascio senza chiave: crea `key.properties` (sezione «APK Android») o usa `android dev` per provare. |
| Android: l'APK si compila ma non si installa («app non installata») | Un APK firmato con una chiave diversa non aggiorna quello già sul telefono: disinstalla prima la versione precedente. |

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
  (`msiexec /i "Civil Beefy_x.y.z_x64_it-IT.msi" /qn`) via Intune o criteri di gruppo; in futuro si
  potrà precompilare l'indirizzo del server con una proprietà dell'MSI.
- **macOS e Linux**: rimandati. **iOS**: richiede un Mac e un account sviluppatore Apple.
- **Aggiornamenti dell'app Android** ([#32](https://github.com/giorgiorest/gestionale/issues/32)).
  L'APK sta fuori dal Play Store, quindi oggi l'utente lo riscarica a mano dalla pagina del server.
  Nella stessa issue il `versionCode`, che segue `version` di `tauri.conf.json`: va aumentato a ogni
  rilascio, altrimenti Android non vede un aggiornamento. `tauri-plugin-updater` (issue
  [#4](https://github.com/giorgiorest/gestionale/issues/4)) non copre Android: serve un altro
  meccanismo.
- **Android, login nel browser di sistema**: Custom Tab invece della WebView dell'app (RFC 8252),
  come il punto già aperto per il desktop.
