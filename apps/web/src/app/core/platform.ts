/**
 * true se l'app gira dentro l'app desktop installata (Tauri), servita dal protocollo interno
 * (`http://tauri.localhost` su Windows, `tauri://localhost` su macOS/Linux).
 *
 * Con `tauri dev` la finestra carica `http://localhost:4200`: in quel caso l'app si comporta
 * come la web (stessa configurazione, stesso proxy /api), e questa funzione restituisce false.
 */
export function isPackagedDesktopApp(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  const inTauri = '__TAURI_INTERNALS__' in window || (window as { isTauri?: boolean }).isTauri === true;
  const { protocol, hostname } = window.location;
  return inTauri && (protocol === 'tauri:' || hostname === 'tauri.localhost');
}
