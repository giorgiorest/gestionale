# ADR 0006 — Interfaccia con Angular Material e marchio configurabile

- **Stato**: accettata
- **Data**: 2026-11-23
- **Sostituisce**: la scelta di Ionic per i componenti dell'interfaccia (ADR 0001)

## Contesto

Il gestionale deve avere un carattere grafico proprio (logo e colori), essere usato
**soprattutto da PC** e restare rivendibile. Serviva una libreria di componenti Angular
gratuita anche per un uso commerciale, mantenuta nel tempo e personalizzabile.

Alternative valutate (versioni compatibili con Angular 22, novembre 2026):

| Libreria | Licenza | Valutazione |
|----------|---------|-------------|
| **Angular Material 22** | MIT | Ufficiale, rilasciata insieme ad Angular; temi Material 3 generati da un colore; basata su variabili CSS |
| PrimeNG 22 | **commerciale** (PrimeUI License) | Gratuita solo sotto 1 M$ di fatturato, 10 dipendenti, 5 sviluppatori; serve una chiave di licenza, altrimenti mostra un avviso. Esclusa per un prodotto da rivendere |
| ng-zorro (Ant Design) 22 | MIT | Molto ricca per i gestionali, aspetto "Ant" riconoscibile e meno personalizzabile |
| Taiga UI 5 | Apache 2.0 | Bella e originale, comunità e componenti dati più ridotti |
| Ionic 9 (in uso) | MIT | Pensata per app mobile; per un uso prevalente da PC aggiunge peso senza vantaggi |

## Decisione

1. **Angular Material 22** come unica libreria di componenti; **Ionic rimosso**.
   Capacitor (mobile) non dipende da Ionic e resta utilizzabile.
2. **Layout per PC**: barra superiore (logo, nome, azienda, notifiche, menu utente) e menu
   laterale fisso filtrato per permessi e piano; sotto i 900 px il menu diventa a scomparsa.
3. **Tema di prodotto** Material 3 generato dal colore del marchio
   (`apps/web/src/styles/_theme-colors.scss`, schematic `@angular/material:theme-color`).
   Nei componenti si usano solo le variabili di sistema (`var(--mat-sys-…)`), mai colori fissi;
   unica eccezione documentata: le tinte ambra degli avvisi (`--app-warning-*`), che Material 3 non prevede.
4. **Font e icone inclusi nell'app** (Inter, OFL; Material Symbols, Apache 2.0) invece di
   Google Fonts: funzionano offline e nell'app desktop, e non trasmettono a Google gli
   indirizzi IP degli utenti (tema GDPR già oggetto di provvedimenti in Europa).
5. **Marchio per installazione** da `config.json` (`branding.name`, `branding.logoUrl`,
   `branding.primaryColor`, `branding.accentColor`), applicato a runtime da `BrandingService`: nome
   nella barra e nel titolo, logo, colore principale e di accento (le tinte derivate si calcolano con
   `color-mix`). Nell'immagine Docker arriva dalle variabili `BRAND_NAME`, `BRAND_LOGO_URL`,
   `BRAND_PRIMARY_COLOR`, `BRAND_ACCENT_COLOR`.
6. **Colore di accento** `#F2B300` (giallo del logo) come "tertiary" di Material 3, con testo scuro
   sopra: linea sotto la barra superiore, contatori delle notifiche, linea in cima alla scheda di
   login. Solo come riempimento, mai come colore del testo su sfondo chiaro (contrasto insufficiente).
   Gli stati con un significato (errore, da confermare) mantengono i propri colori.
   Il colore viene validato (`#RRGGBB`) per evitare iniezioni di CSS.

## Conseguenze

- Bundle iniziale della web app sceso da 645 kB a 430 kB.
- Marchio di prodotto: simbolo a celle esagonali (`apps/web/public/branding/logo.svg`, usato anche
  per favicon, icone PWA e desktop) e colore principale `#0B5C8E`; procedura per cambiarli in
  `apps/web/README.md`.
- Il colore per cliente modifica il colore principale e le sue tinte; la palette completa
  (secondario, terziario, neutri) resta quella di prodotto.
- Tabelle molto avanzate (raggruppamenti, export, modifica in linea) andranno affiancate da
  una griglia dedicata (es. AG Grid Community, MIT) quando serviranno.
- Da fare: tema chiaro/scuro a scelta dell'utente, tema di login Keycloak con lo stesso marchio,
  esportazione originale del simbolo (ora ridisegnato in SVG da un'immagine).
