# ADR 0012 — Verticale di prodotto: edilizia e cantieri

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

La piattaforma è completa (multi-tenant con RLS, identità, licenze, notifiche, console di
piattaforma, web/desktop/Android, rilascio con approvazione), ma il **dominio applicativo è vuoto**:
l'unica tabella di dominio è `customers`, con nome ed email ([V2](../../apps/api/src/main/resources/db/migration/V2__customers.sql)).

Un «gestionale generico» non è vendibile: non si può scrivere un listino, non si può fare una demo,
non si può rispondere a «cosa fa per me». Serve scegliere **a chi** è destinato, perché la scelta
determina il modello dati, l'ordine di sviluppo e il messaggio commerciale.

Il vantaggio tecnico già costruito è uno e preciso: **funziona senza connessione**, sulla stessa base
di codice, anche su telefono (PWA, Windows, APK Android). I gestionali web classici in cantiere, in
cave, in locali tecnici e nei sotterranei non funzionano.

## Decisione

Il prodotto è **il gestionale per le imprese edili e di cantiere**: imprese di costruzioni e
ristrutturazioni, impiantisti, aziende di manutenzione che lavorano per commessa fuori sede.

Conseguenze dirette sul dominio:

1. **La commessa (il cantiere) è l'entità centrale**, non il documento. Tutto si appende alla
   commessa: preventivo, rapportini, materiali, allegati, documenti, costi, margine.
2. **Il rapportino giornaliero di cantiere è il documento più usato del prodotto**, compilato dal
   telefono, offline, da chi è in cantiere: ore per persona, materiali impiegati, note, foto.
   È la funzionalità che giustifica l'architettura offline-first e che differenzia il prodotto.
3. **Le anagrafiche sono fiscali e complete** (clienti *e* fornitori): ragione sociale, P.IVA,
   codice fiscale, indirizzo, PEC, codice destinatario SDI, condizioni di pagamento. Senza questi
   campi non si emette un documento in Italia.
4. **Il linguaggio dell'interfaccia è quello del mestiere**: commessa, cantiere, rapportino,
   squadra, DDT, SAL, preventivo. Non «progetto», «task», «ticket».
5. **Offline e mobile non sono un extra**: una funzionalità che non funziona senza campo, in
   cantiere, è una funzionalità incompleta.

## Fuori perimetro (per ora, con il motivo)

- **Contabilità generale e dichiarativi**: restano al commercialista. Il prodotto arriva al
  documento e allo scadenzario, non al bilancio.
- **Paghe e cedolini**: normativa e CCNL edili cambiano spesso, serve un consulente del lavoro.
  Il prodotto raccoglie le **ore**, che è ciò che serve a chi fa le paghe.
- **Computo metrico estimativo con prezzari regionali / DEI**: utile e molto richiesto, ma è un
  progetto a sé (licenze dei prezzari comprese). Da valutare come modulo a pagamento in seguito.
- **Sicurezza documentale (POS, PSC, DUVRI, scadenze visite e corsi)**: candidato forte per una
  seconda fase; oggi non blocca la vendita.
- **BIM, CAD, modellazione**: altro mercato, altro prodotto.
- **iOS**: già escluso in [ADR 0011](0011-app-android.md).

## Alternative scartate

- **Gestionale generico multi-settore**: massimizza il mercato teorico e azzera quello reale.
  Nessuna PMI compra uno strumento che «si può adattare»: compra quello che parla la sua lingua.
- **Studi professionali / commercialisti**: mercato presidiato da operatori enormi e l'offline non
  vale nulla in ufficio, quindi il nostro unico vantaggio tecnico sparisce.
- **Officine e riparazioni**: stesso schema (commessa + rapportino), ma il lavoro è in sede: di
  nuovo l'offline non serve. Resta un possibile mercato adiacente da riusare in seguito.

## Conseguenze sull'ordine di sviluppo

L'ordine delle fasi è in [docs/roadmap.md](../roadmap.md). In sintesi: prima la sincronizzazione
vera e le anagrafiche fiscali, poi commesse e rapportini (il differenziante), poi i documenti e la
fatturazione elettronica (ciò che fa firmare), poi il resto.
