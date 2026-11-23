# ADR 0004 — App desktop con Tauri: un installer per tutti i clienti

- **Stato**: accettata
- **Data**: 2026-11-23

## Contesto

Serve un'app desktop Windows del gestionale, da distribuire ai clienti del prodotto.
La web app esiste già (Angular + Ionic) e deve restare l'unico codice dell'interfaccia.

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

## Alternative scartate

- **Electron**: installer e memoria molto più pesanti, nessun vantaggio per una UI web esistente.
- **Configurazione inclusa nell'installer**: un installer per cliente da produrre e firmare a ogni
  rilascio; non scala con la rivendita.
- **Stesso client OIDC della web**: mescola redirect URI di origini diverse e impedisce di
  gestire il canale desktop separatamente.
