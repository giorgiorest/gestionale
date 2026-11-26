# ADR 0022 — Osservabilità: log JSON, metriche Prometheus, avviso sulla salute

- **Stato**: accettata
- **Data**: 2026-11-26

## Contesto

Voce 0.4 della roadmap (Fase 0 — processo di sviluppo). Fino a oggi l'unico segnale operativo è
`GET /api/health` (liveness pubblico, usato da compose e load balancer): nessun log strutturato,
nessuna metrica, nessun avviso quando l'app smette di funzionare correttamente. Con un'unica
istanza multi-tenant (Row Level Security), un log che non distingue tenant e richieste rende
qualsiasi indagine su un problema di un singolo cliente una ricerca a tentativi.

## Decisione

1. **Log JSON con `logstash-logback-encoder`** (non un encoder scritto a mano): libreria
   mantenuta, già pensata per Logback/Spring Boot, nessuna configurazione server-side aggiuntiva
   (l'aggregatore di log, se ne arriva uno, legge JSON da stdout). In sviluppo (`dev`) i log
   restano leggibili a schermo con il pattern di default di Spring Boot: il JSON serve a chi
   aggrega i log, non a chi guarda la console durante lo sviluppo.
2. **`request_id` e `tenant_id` in MDC**, non passati esplicitamente a ogni log. Un solo filtro
   (`RequestLoggingContextFilter`, registrato dopo `TenantFilter` nella stessa catena di
   `SecurityConfig`) legge/genera l'id di richiesta (header `X-Request-Id`, restituito anche in
   risposta per la correlazione lato client) e il tenant corrente da `TenantContext` — già
   popolato da `TenantFilter` a quel punto della catena — e li rimuove a fine richiesta. Il codice
   di dominio non cambia: i log che già esistono guadagnano i due campi gratis.
3. **Metriche con Micrometer + `micrometer-registry-prometheus`**, `GET /actuator/prometheus`
   esposto (`management.endpoints.web.exposure.include`). Protetto solo a livello di rete, non da
   Spring Security con un JWT: un raccoglitore di metriche (Prometheus) non ha un token utente, e
   l'endpoint non espone dati di un tenant (solo contatori/timer aggregati dell'istanza). La
   protezione è lo stesso meccanismo già in uso per `/console` e `/api/platform/**`: un blocco
   `/actuator/*` nel Caddyfile dietro `ADMIN_ALLOWED_IPS` (nessuna variabile nuova). Senza questo
   blocco l'endpoint sarebbe comunque irraggiungibile da Internet (il catch-all instrada tutto il
   non-`/api/*` a `web:80`, non all'API), ma senza di esso un futuro Prometheus sulla stessa
   macchina non potrebbe raggiungerlo nemmeno dalla rete interna via Caddy.
4. **Avviso sulla salute senza un servizio di alerting esterno**: `HealthAlertWatchdog`
   (`@Scheduled`, un minuto) legge `HealthEndpoint` **in-process** (stesso aggregato di
   `/actuator/health`, senza bisogno di esporlo per questo) e avvisa solo sulla transizione
   (degrado e ripristino), non a ogni controllo — altrimenti un disservizio prolungato manderebbe
   un'email al minuto. L'avviso è sempre un log `ERROR` (visibile a chiunque legga i log) e, se
   `HEALTH_ALERT_EMAIL` è impostata, anche un'email, con lo stesso server SMTP e lo stesso
   mittente (`MAIL_FROM`) già usati da `gestionale.notifications.email`.
5. **Non passa da `NotificationService`/`TenantContext`/`WorkerContext`.** La salute
   dell'applicazione non è un dato di un tenant né una tabella tecnica multi-tenant: è stato
   dell'istanza. Infilarla nel canale notifiche per-tenant (pensato per i dipendenti di
   un'azienda cliente) o dietro un `WorkerContext` (pensato per processi che leggono tabelle con
   RLS dedicata) avrebbe aggiunto complessità senza bisogno: qui non c'è una tabella da leggere.

## Conseguenze

- Un'indagine su un tenant specifico parte dal suo `tenant_id`, non da una ricerca testuale nei
  log; una richiesta end-to-end (client → API → eventuale errore) si segue con lo stesso
  `request_id` lungo tutta la catena.
- `/actuator/prometheus` resta un endpoint senza autenticazione applicativa: se in futuro
  l'installazione cambia e la rete amministrativa non filtra più l'accesso (es. Caddy sostituito),
  va rivista insieme a questa decisione — oggi la rete è l'unico confine, come per la console.
- Nessuna dashboard (Grafana) o alerting esterno (Prometheus Alertmanager) in questa voce: non
  c'è ancora un Prometheus distribuito nell'infrastruttura (`infra/deploy/oracle/compose.yml` non
  lo contiene). Chi lo aggiungerà in futuro troverà già l'endpoint e la rete pronti.
- Una singola istanza dell'API ha il proprio stato di "ultimo controllo": con più istanze dietro
  lo stesso load balancer (oggi non è il caso), ciascuna avviserebbe in modo indipendente — non è
  un problema di questa voce, va rivisto se/quando si introduce la replica.

## Alternative scartate

- **Un `HealthIndicator` personalizzato invocato da uno scheduler che chiama l'endpoint HTTP**:
  scartata. `HealthEndpoint` è già un bean Spring quando `spring-boot-starter-actuator` è sul
  classpath, indipendentemente da cosa è esposto via HTTP (`management.endpoints.web.exposure`):
  chiamarlo in-process evita un giro HTTP su se stessi e non obbliga a esporre `/actuator/health`
  da nessuna parte per usarlo internamente.
- **Canale notifiche esistente (`NotificationService`) per l'avviso di salute**: scartata (vedi
  decisione 5): avrebbe richiesto un tenant fittizio o un giro per tutti i tenant via
  `WorkerContext`, per un'informazione che non appartiene a nessun tenant.
- **Proteggere `/actuator/prometheus` con un JWT come le altre API**: scartata. Un raccoglitore di
  metriche non ha un utente che fa login; avrebbe richiesto un client service-account OAuth2 solo
  per Prometheus, per un endpoint che la rete amministrativa già isola.
