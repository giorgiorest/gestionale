# ADR 0011 — App Android con Tauri, al posto di Capacitor

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

Il prodotto gira già come **web/PWA** e come **app desktop Windows** ([ADR 0004](0004-app-desktop.md)),
con un solo codice di interfaccia in `apps/web`. Serve ora un'app **Android** installabile
(APK firmato), senza toccare le due piattaforme esistenti.

Lo stack iniziale ([ADR 0001](0001-stack.md)) indicava **Capacitor** per il mobile, scelto quando
il frontend era Ionic. Ionic è stato poi rimosso ([ADR 0006](0006-interfaccia-angular-material.md))
e Capacitor non è mai stato introdotto: non c'è codice da riusare né da buttare.

Nel frattempo **Tauri 2**, già in casa per il desktop, supporta Android: stessa configurazione,
stessi permessi (`capabilities/`), stesso punto di ingresso Rust.

## Decisione

1. **L'app Android la produce Tauri 2**, dallo stesso progetto `apps/desktop/src-tauri`
   (`npx tauri android init` → `src-tauri/gen/android/`, versionata). Capacitor non si adotta.
2. **Nessuna build Angular separata**: l'APK incapsula la build di produzione di `apps/web`,
   come fa l'installer Windows.
3. **Nessun ramo di codice per Android nel frontend.** Le differenze reali sono due e si
   risolvono senza rilevare la piattaforma:
   - l'app impacchettata (desktop o Android) si riconosce già da `isPackagedDesktopApp()`,
     perché anche su Android Tauri serve i file da `http://tauri.localhost`: ne discendono
     già scelta del server al primo avvio, niente service worker e client OIDC `gestionale-desktop`;
   - le aree sicure (barra di stato, notch, barra di navigazione) si gestiscono in CSS con
     `env(safe-area-inset-*)` e `viewport-fit=cover`, che nel browser valgono 0.

   Non si è aggiunto `@tauri-apps/plugin-os`: il frontend non ha dipendenze da Tauri e non c'è
   un comportamento che richieda di distinguere Android da Windows.
4. **Finestra**: le dimensioni fisse del desktop (`width`, `minWidth`…) non valgono su un telefono.
   Invece di toglierle si sovrascrivono solo per Android in `tauri.android.conf.json`, che Tauri
   unisce alla configurazione base: il desktop resta identico.
5. **Login**: si riusa il client Keycloak `gestionale-desktop`, le cui origini di ritorno
   (`http://tauri.localhost/*`) sono esattamente quelle dell'app Android. **Non** serve il client
   con redirect URI nativi previsto da [ADR 0003](0003-identita-keycloak.md) per Capacitor.
6. **Firma**: l'APK si firma con una chiave di rilascio che non sta nel repository. In CI arriva
   dai GitHub Secrets; senza chiave la build di rilascio **si ferma con un errore**, invece di
   produrre un APK non firmato. Identificatore dell'app invariato (`it.gestionale.desktop`):
   cambiarlo creerebbe un'app diversa agli occhi di Android e del desktop.
7. **CI e distribuzione**: `.github/workflows/android.yml` (runner Linux) produce l'APK come
   artifact sulle pull request; su `main` lo richiama *Release immagini*, che lo include
   nell'**immagine web dello stesso commit** insieme agli installer Windows. Gli utenti lo
   scaricano dalla pagina `/app-desktop` del server della loro azienda, come il setup Windows:
   versione dell'app e del server restano allineate, anche nei ritorni indietro.
   L'APK è **facoltativo** in `desktop.json`: se la build Android fallisce la pagina mostra solo
   Windows, invece di sparire del tutto.

## Conseguenze

- Una sola toolchain (Rust + Tauri) per desktop e Android: un ambiente in meno da mantenere,
  e i plugin nativi si scrivono una volta sola.
- `src-tauri/gen/android/` è codice generato ma **versionato**, perché contiene la configurazione
  di firma. Rigenerarlo con `tauri android init` sovrascrive quelle modifiche: vanno rimesse.
- I plugin solo desktop (vassoio, scorciatoie globali, aggiornamento automatico) vanno dichiarati
  nella sezione condizionale di `Cargo.toml` e protetti con `#[cfg(desktop)]`, altrimenti la
  compilazione per Android fallisce.
- L'app Android eredita i limiti dell'app desktop: il login avviene nella finestra dell'app, non
  nel browser di sistema (RFC 8252). Su Android la pratica raccomandata sono le Custom Tabs:
  da valutare insieme al punto già aperto per il desktop.
- **Niente aggiornamenti automatici**: l'APK si distribuisce fuori dal Play Store, quindi
  l'utente torna sulla pagina `/app-desktop` e riscarica. Perché Android riconosca un
  aggiornamento serve un `versionCode` più alto, che Tauri ricava da `version` di
  `tauri.conf.json`: va aumentato a ogni rilascio.
- nginx deve servire l'APK come `application/vnd.android.package-archive`: con il tipo generico
  Android scarica il file ma non propone di installarlo.
- `minSdk` 24 (Android 7): sotto, la WebView di sistema è troppo vecchia.
- iOS resta fuori: richiede un Mac e un account sviluppatore Apple.

## Alternative scartate

- **Capacitor**, come da ADR 0001: avrebbe aggiunto un secondo contenitore nativo accanto a Tauri,
  con un proprio ciclo di build e propri plugin, per lo stesso risultato. Aveva senso con Ionic,
  non più oggi.
- **Solo PWA installabile da Chrome**: non dà un APK da distribuire, non passa dagli store e su
  Android l'installazione dal browser è poco riconoscibile per un utente aziendale.
- **Togliere `minWidth`/`minHeight` da `tauri.conf.json`** invece di usare
  `tauri.android.conf.json`: avrebbe cambiato il comportamento della finestra desktop.
- **Rinominare `isPackagedDesktopApp()`** in tutto il frontend: tanto rumore nel diff per un
  cambio di nome; il significato («app impacchettata») è documentato nella funzione.
