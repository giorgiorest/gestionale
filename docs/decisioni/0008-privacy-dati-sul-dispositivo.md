# ADR 0008 — Privacy: cookie tecnici, dati sul dispositivo e registro degli accessi

- Stato: accettata
- Data: 2026-11-23

## Contesto

Civil Beefy viene venduto ad aziende che lo usano con i dati dei dipendenti e dei loro clienti: chi
lo vende in SaaS è responsabile del trattamento (art. 28 GDPR). Un'analisi del codice ha mostrato:

- cookie solo tecnici (Keycloak) e nessun contenuto di terze parti, ma nessuna informativa nell'app;
- il database offline del browser (IndexedDB) restava dopo l'uscita e non era legato all'utente:
  sullo stesso PC un altro utente, anche di un'altra azienda, poteva vedere la copia locale dei dati
  del precedente (ad esempio se il server non rispondeva) e la sua outbox poteva essere inviata con
  il token sbagliato;
- Keycloak non registrava gli accessi.

## Decisione

- **Solo cookie e archiviazioni tecniche**, quindi niente banner di consenso (Linee guida del Garante
  del 10 giugno 2021). L'informativa è una pagina pubblica `/privacy` uguale per ogni installazione,
  con i link ai documenti del titolare da `config.json` (`legal.privacyUrl`, `legal.termsUrl`;
  con Docker `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`).
- **Dati offline legati all'utente**: il database locale registra il proprietario (`tenant_id:sub`,
  letto dal token, quindi anche offline). All'avvio, se è cambiato, il database si svuota prima del
  routing. Dati senza proprietario (versioni precedenti) passano all'utente corrente, per non perdere
  modifiche non inviate.
- **Uscita** (`LogoutService`): prova a inviare la outbox; se restano modifiche chiede conferma; poi
  svuota il database locale e chiude la sessione. «Cambia server» nell'app desktop svuota anch'esso.
- **Eventi di Keycloak**: accessi riusciti e falliti conservati 90 giorni; operazioni degli
  amministratori registrate.
- Gli obblighi non tecnici (DPA, registro, data breach, informative) e i passi mancanti sono in
  [docs/privacy-gdpr.md](../privacy-gdpr.md).

## Conseguenze

- Uscendo offline con modifiche non inviate, l'utente può scegliere di perderle: è il compromesso
  per non lasciare dati personali sul dispositivo.
- Ogni nuovo cookie o archivio nel browser va aggiunto alla pagina `/privacy` e a `docs/privacy-gdpr.md`;
  strumenti di statistica o contenuti esterni richiederebbero un banner di consenso.
- Il realm si importa solo al primo avvio: nelle installazioni esistenti gli eventi si attivano dalla
  console.

## Alternative scartate

- **Banner di consenso**: inutile con soli cookie tecnici e peggiora l'esperienza.
- **Cifrare IndexedDB** con una chiave legata alla sessione: protegge anche da chi accede al disco,
  ma complica l'uso offline (la chiave dovrebbe sopravvivere alla chiusura della scheda). Rimandata.
- **Un database locale per utente** (nome con `sub`): evita la cancellazione al cambio utente, ma
  lascia comunque sul PC i dati di tutti gli utenti passati.
