# ADR 0003 — Identità e profilazione con Keycloak, pensate per la rivendita

- **Stato**: accettata
- **Data**: 2026-11-23

## Contesto

Il gestionale deve autenticare gli utenti, associarli a un tenant (azienda cliente) e
limitare le funzioni in base al ruolo. Il prodotto verrà **rivenduto**: servono quindi

- installazioni ripetibili, senza configurazione manuale "a mano" per ogni cliente;
- la possibilità di servire più clienti da un'unica istanza (SaaS) **e** di installare il
  prodotto presso un cliente (on-premise o cloud dedicato);
- clienti che vorranno usare il **proprio** login aziendale (Microsoft Entra ID, Google Workspace, LDAP);
- nessun costo di licenza per componente venduto e nessun vincolo verso un fornitore di identità.

## Decisione

### Provider: Keycloak 26

- Open source (Apache 2.0): si può distribuire e rivendere senza royalty.
- Standard OIDC/OAuth2; federazione LDAP/AD e *identity brokering* verso Entra ID, Google, SAML.
- Pagine di login, recupero password, MFA, console account dell'utente già pronte e personalizzabili (temi).

### Configurazione come codice

- `infra/keycloak/realm/gestionale-realm.json`: **configurazione di prodotto**, uguale per ogni
  installazione (ruoli, client, mapper, policy password, lingua italiana).
  Gli URL variano con le variabili d'ambiente (`${GESTIONALE_WEB_URL}`), non modificando il file.
- `infra/keycloak/dev/gestionale-users-0.json`: **solo sviluppo**, utenti demo. Non va mai caricato
  in un'installazione reale.

### Contratto tra provider e applicazione (neutro rispetto al fornitore)

L'API e la web app dipendono solo da claim OIDC standard più due claim nostri:

| Claim | Contenuto | Origine in Keycloak |
|-------|-----------|---------------------|
| `sub`, `preferred_username`, `name`, `email` | identità dell'utente | standard |
| `tenant_id` | UUID del tenant | attributo utente `tenant_id` (modificabile solo dagli amministratori) |
| `roles` | ruoli applicativi | ruoli di realm `admin`, `operator`, `viewer` |
| `aud` | contiene `gestionale-api` | mapper audience sul client |

Qualsiasi altro provider che emetta questi claim è sostituibile senza toccare il codice.
Nel frontend si usa `oidc-client-ts` (libreria OIDC generica), non l'adapter di Keycloak.

### Profilazione: ruoli → permessi nel backend

- In Keycloak si assegnano solo **ruoli** (profili): `admin`, `operator`, `viewer`.
- La mappatura ruolo → **permessi** (`customers:read`, `customers:write`, `assistant:use`,
  `users:manage`) sta nel codice (`Role`/`Permission` in `apps/api/.../auth`). Così cambiare
  cosa può fare un ruolo è una modifica versionata e testata, non una configurazione per cliente.
- Gli endpoint controllano i permessi con `@PreAuthorize`; il frontend li usa solo per la UI
  (`*appHasPermission`, `permissionGuard`).
- Ruoli (chi sei nell'azienda) ed entitlement (cosa ha comprato l'azienda) restano distinti:
  una funzione è disponibile solo se il piano la include **e** il ruolo la consente.

### Client

- `gestionale-web`: client **pubblico** con Authorization Code + **PKCE** (S256), nessun segreto
  nell'app distribuita. Vale per web/PWA e desktop (Tauri). Per le app mobile (Capacitor) si
  aggiungerà un client con redirect URI nativi (`it.gestionale.app://…`).
- Flusso password diretto disattivato; registrazione libera disattivata (gli utenti li crea l'amministratore).

### Modelli di installazione

1. **SaaS multi-tenant** (un'istanza per molti clienti): un solo realm `gestionale`, ogni utente
   ha `tenant_id`. Isolamento garantito dalla RLS di PostgreSQL.
2. **Istanza dedicata** (cloud dedicato o on-premise): stesso realm importato nel Keycloak del
   cliente, di solito con un solo tenant.
3. **Login aziendale del cliente**: in entrambi i casi si configura un *identity provider* nel
   realm (Entra ID, Google…) con un mapper che assegna `tenant_id` e ruolo di default agli utenti
   di quel dominio. L'applicazione non cambia.

## Conseguenze

- Pagina di login con il marchio del prodotto: tema `gestionale` (eredita da `keycloak.v2`),
  personalizzabile per cliente con un tema figlio che ridefinisce colore e logo.
- In produzione Keycloak gira con database PostgreSQL proprio, HTTPS, `KC_HOSTNAME` pubblico e
  credenziali di bootstrap solo da variabili d'ambiente (vedi `infra/keycloak/README.md`).
- Creare un tenant richiede due passi: riga in `tenants` (con licenza) e utenti in Keycloak con
  quel `tenant_id`. Da automatizzare con un'area di amministrazione (permesso `users:manage`)
  che usi l'Admin API di Keycloak con un service account dedicato.
- `maxUsers` della licenza non è ancora applicato: andrà controllato alla creazione degli utenti.
- Con un'unica istanza SaaS e molti clienti con login aziendale, valutare le *Organizations* di
  Keycloak (un'organizzazione per tenant, con domini e IdP propri) al posto dell'attributo semplice.
- Il login richiede rete: l'uso offline prolungato (sessione scaduta senza connessione) è da
  progettare insieme alla sincronizzazione.

## Alternative scartate

- **Auth0 / Entra ID / Cognito come unico provider**: costi per utente che crescono con la
  rivendita e vincolo verso il fornitore; restano collegabili tramite brokering.
- **Autenticazione fatta in casa**: rischio di sicurezza e costi di manutenzione (MFA, recupero
  password, lockout) senza vantaggi.
- **Un realm per tenant**: isolamento forte ma gestione pesante con molti clienti; resta
  possibile per le istanze dedicate.
