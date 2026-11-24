# ADR 0017 — Fatturazione elettronica: il canale SDI sta dietro un'interfaccia

- **Stato**: **parzialmente accettata** — la scelta del fornitore è in sospeso
- **Data**: 2026-11-24

## Contesto

Nessuna azienda italiana compra un gestionale che non emette fattura elettronica. Il formato è
**FatturaPA** (XML) e il canale è lo **SdI** dell'Agenzia delle Entrate, raggiunto in pratica
tramite un intermediario accreditato con API.

Il fornitore **non è ancora scelto**: va valutato commercialmente e contrattualmente. Ma la scelta
architetturale si può prendere subito, e prenderla ora evita di riscrivere il modulo quando il
fornitore sarà deciso.

C'è un vincolo che cambia tutto e va chiarito con ogni fornitore prima di firmare: **Civil Beefy è
multi-tenant**. Una sola istanza emette fatture **per conto di molte aziende diverse**, ognuna con
la propria partita IVA. Serve quindi un rapporto da **intermediario / multi-cedente con delega**,
non un account legato a una singola azienda. Molti listini «per azienda» non coprono questo caso.

## Decisione (la parte accettata)

1. **La generazione dell'XML FatturaPA sta nel prodotto**, non nel fornitore: è dominio nostro,
   deve essere testabile senza rete e non deve dipendere dall'API di nessuno.
2. **Il canale di trasmissione sta dietro un'interfaccia** (`SdiChannel` o nome equivalente) nel
   modulo di fatturazione dell'API, con le operazioni minime: invio, stato, ricezione delle fatture
   passive, notifiche (consegna, scarto, mancata consegna). Stessa impostazione di `IdentityAdmin`
   ([ADR 0010](0010-console-piattaforma.md)) e per la stessa ragione: il prodotto è rivenduto e il
   fornitore deve restare sostituibile.
3. **Gli stati del documento e le notifiche si memorizzano da noi** (tabella con `tenant_id` e RLS):
   l'anagrafe di cosa è stato inviato, accettato, scartato e perché non può vivere solo presso il
   fornitore.
4. **Un'implementazione di prova** (equivalente di `NoopAiProvider`) permette di sviluppare e
   testare tutto il ciclo senza contratto e senza rete.
5. **La chiave del fornitore arriva come segreto di rete dell'ambiente**, mai nel repository; e
   l'host del fornitore va aggiunto alla rete consentita dell'ambiente di sviluppo quando si
   comincerà a integrarlo.
6. La **conservazione sostitutiva a norma** (10 anni) è un servizio, non codice nostro: va compresa
   nel contratto con il fornitore oppure affidata a un conservatore accreditato. Da verificare,
   non da implementare.

## Da decidere: il fornitore

Candidati da valutare (API REST, mercato italiano): **Aruba**, **A-Cube**, **TeamSystem / Fatture in
Cloud**, **Agyo**, **Openapi.it**. L'elenco non è una graduatoria e i listini non sono riportati
qui: vanno chiesti, perché cambiano.

Domande da porre a ciascuno, in questo ordine di importanza:

1. **Multi-cedente / intermediario**: si possono emettere fatture per conto di molte aziende con
   un unico contratto e un'unica integrazione? Come si gestisce la **delega** di ciascuna azienda?
   *(Se la risposta non è chiara, il fornitore non va bene per questo prodotto.)*
2. **Ricezione** delle fatture passive e **notifiche** dello SdI: sono incluse e recapitate via API
   o webhook?
3. **Conservazione a norma** inclusa, opzionale o assente?
4. **Ambiente di prova** (sandbox) utilizzabile senza contratto di produzione?
5. **Costo**: per azienda, per fattura, a scaglioni? Con quanti clienti conviene cambiare modello?
6. **Contratto e privacy**: è disponibile un accordo come responsabile del trattamento (art. 28
   GDPR)? Dove sono i dati?
7. **Uscita**: come si esporta lo storico se si cambia fornitore?

## Conseguenze

- Il modulo di fatturazione si può progettare e implementare **adesso**, fino all'XML e agli stati,
  con l'implementazione di prova. Solo l'ultimo pezzo (il trasporto reale) attende la decisione.
- Quando il fornitore sarà scelto, questo ADR passa a «accettata» e si aggiunge la motivazione della
  scelta; l'implementazione dell'interfaccia è una pull request a sé.
- Le nuove variabili di produzione (endpoint, credenziali del fornitore) vanno aggiunte a
  `.env.example`, al `compose.yml` di produzione e alla guida (regola di progetto).

## Alternative scartate

- **Collegarsi direttamente allo SdI** (canale accreditato proprio, PEC o web service): richiede
  accreditamento, certificati e un impegno di conformità continuativo. Fuori scala per ora.
- **Scegliere il fornitore adesso, senza valutazione**: legherebbe il prodotto a un contratto preso
  alla cieca, su un vincolo (il multi-cedente) che non tutti i fornitori soddisfano.
- **Lasciare la fattura al commercialista del cliente**: è ciò che accade oggi senza il prodotto, ed
  è esattamente il motivo per cui il cliente non lo comprerebbe.
