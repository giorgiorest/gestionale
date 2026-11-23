# Keycloak — provider di identità

Decisioni e motivazioni: [ADR 0003](../../docs/decisioni/0003-identita-keycloak.md).

| File | Uso |
|------|-----|
| `realm/gestionale-realm.json` | Configurazione di prodotto del realm `gestionale`: ruoli, client `gestionale-web`, mapper dei claim, policy password, lingua. Uguale per ogni installazione. |
| `dev/gestionale-users-0.json` | **Solo sviluppo**: utenti demo del tenant demo. Non caricarlo mai in un'installazione reale. |

## Avvio in sviluppo

Con compose parte da solo (`docker compose -f infra/docker/compose.yml up`).
Da solo, con Podman o Docker, dalla radice del repository:

```bash
podman run -d --name gestionale-keycloak -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -e GESTIONALE_WEB_URL=http://localhost:4200 -v ./infra/keycloak/realm/gestionale-realm.json:/opt/keycloak/data/import/gestionale-realm.json:ro,Z -v ./infra/keycloak/dev/gestionale-users-0.json:/opt/keycloak/data/import/gestionale-users-0.json:ro,Z quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm
```

- Console di amministrazione: <http://localhost:8180/admin> (admin / admin in sviluppo).
- `GESTIONALE_WEB_URL` è l'indirizzo della web app: `http://localhost:4200` con `npm start`,
  `http://localhost:8081` con compose. Finisce nei redirect URI e nei web origins del client.
- L'import usa la strategia *ignora se esiste*: per ricaricare il realm dopo una modifica al file
  ricrea il container (in `start-dev` i dati non sono persistenti).

### Utenti demo (tenant demo)

Password per tutti: `demo-password`.

| Utente | Ruolo | Cosa può fare |
|--------|-------|---------------|
| `admin.demo` | admin | tutto |
| `operatore.demo` | operator | consultare e modificare i clienti, usare l'assistente |
| `lettore.demo` | viewer | consultare i clienti |

## Contratto con l'applicazione

L'API accetta un token solo se: firma valida, `iss` = issuer configurato (`JWT_ISSUER_URI`),
`aud` contiene `gestionale-api`, claim `tenant_id` con un UUID. I ruoli arrivano nel claim `roles`.

Client del realm:

| Client | Uso | Redirect / origini |
|--------|-----|--------------------|
| `gestionale-web` | web app e PWA | `${GESTIONALE_WEB_URL}` (variabile d'ambiente) |
| `gestionale-desktop` | app desktop Tauri | fissi: `http://tauri.localhost`, `tauri://localhost` (uguali per ogni installazione) |

Entrambi sono pubblici con PKCE e hanno gli stessi mapper:

| Mapper | Claim |
|-------------------------------------|-------|
| `tenant_id` (attributo utente) | `tenant_id` |
| `roles` (ruoli di realm) | `roles` |
| `audience gestionale-api` | `aud` |

L'attributo `tenant_id` è dichiarato nello *user profile* del realm, visibile e modificabile
**solo dagli amministratori**: un utente non può cambiare il proprio tenant dalla console account.

## Operazioni comuni

- **Nuovo utente**: Console → realm `gestionale` → Users → Add user; poi nella scheda
  *Attributes* impostare `tenant_id`, in *Role mapping* assegnare `admin`, `operator` o `viewer`,
  in *Credentials* una password temporanea.
- **Nuovo ruolo applicativo**: aggiungerlo nel realm **e** in `Role` (backend), con i permessi;
  aggiornare anche `ROLE_LABELS` nel frontend e questo documento.
- **Modifiche al realm**: si fanno nel file JSON (configurazione come codice) e si verificano
  ricreando il container. Le modifiche fatte da console vanno riportate nel file.

## Produzione (installazione presso un cliente o SaaS)

Il realm di prodotto si importa allo stesso modo; cambiano la modalità di avvio e le variabili:

- `start --import-realm` (non `start-dev`), con `--optimized` dopo una build dell'immagine;
- database proprio: `KC_DB=postgres`, `KC_DB_URL`, `KC_DB_USERNAME`, `KC_DB_PASSWORD`;
- `KC_HOSTNAME=https://login.<dominio-cliente>` e HTTPS (direttamente o dietro reverse proxy con `KC_PROXY_HEADERS=xforwarded`);
- `KC_BOOTSTRAP_ADMIN_USERNAME`/`KC_BOOTSTRAP_ADMIN_PASSWORD` solo al primo avvio, da segreti;
  poi creare un amministratore nominativo e rimuovere quello temporaneo;
- `GESTIONALE_WEB_URL=https://gestionale.<dominio-cliente>`;
- **non** montare `dev/gestionale-users-0.json`;
- SMTP del realm per recupero password e verifica email; backup del database di Keycloak.

Lato applicazione:

- API: `JWT_ISSUER_URI=https://login.<dominio>/realms/gestionale` (ed eventualmente
  `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` se l'API raggiunge Keycloak da un indirizzo interno);
- web: `OIDC_AUTHORITY` con lo stesso issuer, `OIDC_CLIENT_ID=gestionale-web`.

### Login aziendale del cliente (Entra ID, Google, LDAP)

Si configura nel realm come *Identity provider* (o *User federation* per LDAP/AD), con un
mapper che imposta l'attributo `tenant_id` e il ruolo di default per gli utenti di quel cliente.
L'applicazione non cambia.

### Personalizzazione grafica

Il tema di login si può personalizzare per cliente (logo, colori) con un tema Keycloak
dedicato; da predisporre in `infra/keycloak/themes/` quando servirà.
