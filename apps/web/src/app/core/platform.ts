/**
 * true se l'app gira dentro l'app installata (Tauri), servita dal protocollo interno
 * (`http://tauri.localhost` su Windows e Android, `tauri://localhost` su macOS/Linux).
 *
 * Vale per il desktop e per l'app Android: in entrambe i file sono già sul dispositivo, quindi
 * niente service worker, l'indirizzo del server lo sceglie l'utente al primo avvio (pagina
 * /server) e il client OIDC è `gestionale-desktop`, le cui origini di ritorno sono proprio
 * quelle del protocollo interno. Il nome resta `isPackagedDesktopApp` perché è usato in tutta
 * l'app; il significato è "app impacchettata", non "solo Windows".
 *
 * Con `tauri dev` e `tauri android dev` la finestra carica `http://localhost:4200`: in quel caso
 * l'app si comporta come la web (stessa configurazione, stesso proxy /api) e qui si ha false.
 */
export function isPackagedDesktopApp(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  const inTauri = '__TAURI_INTERNALS__' in window || (window as { isTauri?: boolean }).isTauri === true;
  const { protocol, hostname } = window.location;
  return inTauri && (protocol === 'tauri:' || hostname === 'tauri.localhost');
}
