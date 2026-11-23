# ADR 0004 — App desktop con Tauri: un installer per tutti i clienti

- **Stato**: accettata
- **Data**: 2026-11-23

## Contesto

Serve un'app desktop Windows del gestionale, da distribuire ai clienti del prodotto.
La web app esiste già (Angular; oggi con Angular Material, ADR 0006) e deve restare l'unico codice dell'interfaccia.

## Decisione

1. **Tauri 2** incapsula la build di produzione di `apps/web` (`apps/desktop/src-tauri`).
   Nessuna build Angular separata: l'app riconosce a runtime di girare dentro Tauri
   (`isPackagedDesktopApp()`, origine `http://tauri.localhost` o `tauri://localhost`).
2. **Un solo installer per tutti i clienti.** La configurazione non è inclusa nell'app:
   al primo avvio l'utente indica l'indirizzo del server; l'app scarica `<server>/config.json`,
   rende assoluti gli URL e conserva l'ultima configurazione per l'avvio offline.
   `config.json` è pubblico (nessun segreto) ed è servito con `Access-Control-Allow-Origin: *`.
3. **Login** con un client Keycloak dedicato, `gestionale-desktop` (pubblico, PKCE), con redirect
   e origini fisse del protocollo di Tauri. Separato da `gestionale-web` per poterlo
   disattivare o configurare (es. durata delle sessioni) in modo indipendente.
   Per la prima versione il login avviene nella finestra dell'app.
4. **CORS sull'API** solo per le origini dell'app desktop (default `http://tauri.localhost`,
   `tauri://localhost`, estendibili con `CORS_ALLOWED_ORIGINS`), senza cookie: il token
   viaggia nell'header `Authorization`.
5. **Niente service worker** nell'app desktop: i file sono già locali.
6. **CI**: installer Windows (`.msi` e setup NSIS) generati da `ci-desktop.yml` come artifact.

## Conseguenze

- Il server di ogni installazione deve esporre `config.json` (lo fa l'immagine web) e
  includere `desktopClientId` se si usa un client diverso da `gestionale-desktop`.
- Prima della vendita servono **firma del codice** e **aggiornamenti automatici** firmati
  (dettagli in `apps/desktop/README.md`, tracciati come issue).
- Dati locali (IndexedDB) e sessione sono per profilo utente Windows; cambiando server la
  sessione viene azzerata.
- macOS e Linux sono rimandati.

## Aggiornamento — distribuzione dal server (novembre 2026)

- Gli installer si distribuiscono **dal server dell'azienda**: pagina pubblica `/app-desktop`
  («App per Windows»), con il setup `.exe` per l'utente, l'MSI per il reparto IT, gli SHA-256 e
  l'indirizzo del server da inserire al primo avvio.
- *Release immagini* richiama *CI Desktop* (Windows) e include gli installer nell'**immagine web
  dello stesso commit** (`apps/web/public/downloads/`, con `desktop.json`): versione dell'app e del
  server restano allineate, anche nei ritorni indietro. CI Desktop su `main` non gira più da solo
  (niente build doppie: i minuti Windows costano il doppio).
- Una build desktop fallita non blocca il rilascio del server: l'immagine web esce senza installer
  e la pagina lo segnala.
- Scartati: GitHub Releases (repository privato, gli utenti non hanno accesso) e uno storage
  esterno (un servizio in più da gestire, e un dominio terzo da dichiarare nell'informativa).
- Resta da fare: firma del codice (SmartScreen) e aggiornamenti automatici dell'app.

## Alternative scartate

- **Electron**: installer e memoria molto più pesanti, nessun vantaggio per una UI web esistente.
- **Configurazione inclusa nell'installer**: un installer per cliente da produrre e firmare a ogni
  rilascio; non scala con la rivendita.
- **Stesso client OIDC della web**: mescola redirect URI di origini diverse e impedisce di
  gestire il canale desktop separatamente.
