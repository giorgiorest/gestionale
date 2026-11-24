// Punto di ingresso dell'app impacchettata (Windows e Android). La UI è la build di apps/web:
// qui solo la finestra nativa e i plugin. Aggiornamenti automatici e firma del codice:
// vedi apps/desktop/README.md.
//
// Su Android non esiste main.rs: il sistema carica la libreria condivisa e chiama run()
// attraverso `tauri::mobile_entry_point` (vedi ADR 0011).
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }
      Ok(())
    })
    .run(tauri::generate_context!())
    .expect("error while building tauri application");
}
