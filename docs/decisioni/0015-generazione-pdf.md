# ADR 0015 — Generazione dei PDF: HTML e CSS, non codice

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

Preventivi, DDT, fatture, rapportini e SAL devono uscire in PDF, **con il marchio dell'installazione**
(logo, nome, colore: `branding` in `config.json`, già gestito da `BrandingService`). Serve anche che
il layout si possa adattare per un cliente senza ricompilare l'applicazione.

Il prodotto **viene rivenduto**: la licenza di ogni libreria va verificata prima di adottarla
(regola di progetto, [ADR 0006](0006-interfaccia-angular-material.md)).

## Decisione

1. **Il PDF si genera dal backend**, non dal browser: lo stesso documento deve essere identico via
   web, desktop, Android e quando lo produce un processo pianificato (invio per email).
2. **Il modello è un template HTML con CSS**, non codice che disegna. Template **Thymeleaf**
   (Apache-2.0, già nell'ecosistema Spring Boot) → **openhtmltopdf** (basato su Apache PDFBox) per
   la resa in PDF.
3. **I font sono inclusi nell'applicazione** e incorporati nel PDF: nessun font di sistema, nessun
   CDN (coerente con la regola sui contenuti esterni).
4. **Un template per tipo di documento**, nelle risorse dell'API, con i valori del marchio passati
   dal contesto del tenant. Nessun layout compilato nel codice Java.
5. Alla prima implementazione va **verificata la licenza esatta** della versione adottata
   (openhtmltopdf è un fork di Flying Saucer, distribuito sotto LGPL) e annotata qui.

## Conseguenze

- Cambiare l'aspetto di un documento è una modifica a un file HTML/CSS: la può fare chiunque sappia
  il CSS, non serve toccare Java.
- Un layout dedicato per un cliente diventa possibile senza build separate, coerentemente con la
  regola «la stessa build serve clienti diversi».
- **PDF/A** (per l'archiviazione) è supportato dal motore PDFBox: utile per i documenti da
  conservare. Nota: per la **fattura elettronica il documento legale è l'XML**, non il PDF; il PDF è
  una copia di cortesia per il cliente.
- Il peso dei font incorporati va tenuto d'occhio sulla dimensione dei PDF generati.

## Alternative scartate

- **iText 7**: ottima libreria, ma **AGPL o licenza commerciale**. Per un prodotto rivenduto l'AGPL
  non è praticabile e la licenza commerciale è un costo per installazione. Escluso senza discussione.
- **JasperReports**: potente e con un designer visuale, ma pesante da incorporare, con un suo
  formato di template e una sua curva di apprendimento, per un beneficio che qui non serve.
- **Apache PDFBox da solo** (Apache-2.0): si usa comunque sotto, ma scrivere il layout di una
  fattura posizionando testo a coordinate è lavoro manuale lento e fragile a ogni modifica.
- **wkhtmltopdf o Chrome headless**: resa HTML eccellente, ma significa un binario esterno
  nell'immagine Docker (decine di MB, aggiornamenti di sicurezza propri, wkhtmltopdf non è più
  mantenuto) e un processo da avviare per ogni documento.
- **Generare il PDF nel browser** (jsPDF, pdfmake): il documento dipenderebbe dal dispositivo e non
  sarebbe producibile da un processo lato server.
