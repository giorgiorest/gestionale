# ADR 0001 — Stack tecnologico

- **Stato**: accettata
- **Data**: 2026-11-22

## Contesto

Serve un gestionale per circa 50 utenti per cliente, utilizzabile da web, mobile e desktop,
anche offline, venduto a più aziende con piani diversi e con un assistente AI opzionale.
Il team è ridotto: una sola base di codice client e un backend convenzionale riducono i costi.

## Decisione

| Area | Scelta | Motivazione |
|------|--------|-------------|
| Backend | Spring Boot 3.5, Java 21, Maven | Ecosistema maturo per gestionali (transazioni, validazione, sicurezza), LTS Java |
| Database | PostgreSQL 17 | Row Level Security nativa per il multi-tenant, JSONB, affidabilità |
| Migrazioni | Flyway | Migrazioni SQL versionate e ripetibili, integrate in Spring Boot |
| Autenticazione | OAuth2/OIDC, API come resource server JWT | Standard, provider sostituibile; il tenant viaggia nel token |
| Frontend | Angular 22 + Ionic 9 | Un solo codice per web e mobile, componenti adatti al touch, TypeScript strict |
| Web | PWA (`@angular/pwa`) | Installabile e utilizzabile offline senza store |
| Mobile | Capacitor | Riusa la build web, accesso alle API native |
| Desktop | Tauri 2 | Binari leggeri rispetto a Electron, riusa la build web |
| Dati offline | Dexie (IndexedDB) + outbox | API semplice, transazioni, funziona in tutti i contenitori |
| ID | UUID v7 generati dal client | Creazioni offline senza collisioni, ordinabili nel tempo |
| Multi-tenant | Istanza condivisa, `tenant_id` + RLS | Costi contenuti; l'isolamento è garantito dal database e non solo dal codice |
| Licenze offline | Token firmati Ed25519 | Verificabili senza rete e con la sola chiave pubblica |
| AI | Interfacce `AiProvider`/`AiTool` provider-agnostiche | Evita il lock-in; la scelta del provider resta aperta |
| Test backend | JUnit 5, Testcontainers (PostgreSQL reale) | La RLS si verifica solo su un PostgreSQL vero |
| Test frontend | Vitest (default di Angular 22) | Veloce, senza browser in CI |
| CI | GitHub Actions, immagini su GHCR | Integrato con il repository |
| Sviluppo locale | Docker Compose | Stack completo con un comando |

## Conseguenze

- Ogni nuova tabella di dominio deve avere `tenant_id` e `CALL enable_tenant_rls(...)`.
- L'applicazione usa un ruolo database dedicato non superuser; le migrazioni un utente distinto.
- Servirà un provider OIDC (da scegliere) che emetta il claim `tenant_id`.
- Le scritture dei client passano dalla outbox: le API di scrittura devono essere idempotenti.

## Note di bootstrap

- Lo scheletro Maven è stato creato a mano perché `start.spring.io` non era raggiungibile
  dall'ambiente di bootstrap; è equivalente a quello dello Initializr.
- Spring Boot 3.5.16 (ultima patch 3.5.x) invece di 3.5.0.
