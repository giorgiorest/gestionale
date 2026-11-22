# Desktop (Tauri 2) — da predisporre

L'app desktop **incapsula la build web** di `apps/web`: non c'è codice duplicato.
Tauri fornisce solo la finestra nativa, il packaging (Windows, macOS, Linux) e, se servono,
le API native (file system, notifiche, aggiornamenti).

Questa cartella è volutamente vuota: l'inizializzazione si esegue quando richiesta.

## Passi

1. Installare Rust con `rustup` (<https://rustup.rs>) e le dipendenze di sistema indicate
   nella guida di Tauri per il proprio sistema operativo.
2. Da `apps/desktop`, inizializzare Tauri puntando alla build web
   `../web/dist/web/browser`, in uno dei due modi:
   - `npm create tauri-app@latest`, scegliendo un frontend esistente; oppure
   - aggiungere la CLI come dev dependency e inizializzare:

     ```bash
     npm init -y
     npm install --save-dev @tauri-apps/cli
     npx tauri init
     ```

     Alle domande dell'init rispondere:
     - *web assets location*: `../web/dist/web/browser`
     - *dev server URL*: `http://localhost:4200`
     - *frontend dev command*: `npm --prefix ../web start`
     - *frontend build command*: `npm --prefix ../web run build`
3. Verificare che `src-tauri/target/` resti escluso da git (già presente nel `.gitignore` radice).

## Note

- Tauri incapsula la build web senza duplicare il codice: le modifiche si fanno solo in `apps/web`.
- Il database locale (Dexie/IndexedDB) funziona anche nella webview di Tauri.
- Le licenze offline (token firmati) si verificano con la sola chiave pubblica: la chiave
  privata non va mai inclusa nell'app distribuita.
