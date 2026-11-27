# Keycloak — provider di identità

Decisioni e motivazioni: [ADR 0003](../../docs/decisioni/0003-identita-keycloak.md).

| File | Uso |
|------|-----|
| `realm/gestionale-realm.json` | Configurazione di prodotto del realm `gestionale`: ruoli, client `gestionale-web`, mapper dei claim, policy password, lingua. Uguale per ogni installazione. |
| `themes/gestionale/login` | Tema della pagina di login: logo, colori e font dell'applicazione (eredita da `keycloak.v2`). |
| `dev/gestionale-users-0.json` | **Solo sviluppo**: utenti demo del tenant demo. Non caricarlo mai in un'installazione reale. |
| `realm/piattaforma-realm.json` | Realm degli **amministratori della piattaforma** (console `/console`): ruolo `platform-admin`, client `gestionale-console`, password ≥ 14 caratteri e **OTP obbligatoria** (flusso `browser-otp-obbligatoria`). Nessun utente: il primo si crea a mano ([docs/console-piattaforma.md](../../docs/console-piattaforma.md)). |
| `dev/piattaforma-users-0.json` | **Solo sviluppo**: `admin.piattaforma` / `piattaforma-demo-2026`. |
| `setup/keycloak-setup.sh` | Allineamento idempotente delle installazioni esistenti (servizio compose `keycloak-setup`): client di servizio della console nel realm `gestionale`. |

## Avvio in sviluppo

Con compose parte da solo: da `infra/docker`, `podman-compose up -d`
(vedi [docs/avvio-locale.md](../../docs/avvio-locale.md)). Dopo modifiche al realm o al tema:
`podman-compose up -d --force-recreate keycloak`.

Senza compose, con Podman o Docker, dalla radice del repository:

```bash
podman run -d --name gestionale-keycloak -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -e GESTIONALE_WEB_URL=http://localhost:4200 -v ./infra/keycloak/realm/gestionale-realm.json:/opt/keycloak/data/import/gestionale-realm.json:ro,Z -v ./infra/keycloak/dev/gestionale-users-0.json:/opt/keycloak/data/import/gestionale-users-0.json:ro,Z -v ./infra/keycloak/themes/gestionale:/opt/keycloak/themes/gestionale:ro,Z quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm
```

- Console di amministrazione: <http://localhost:8180/admin> (admin / admin in sviluppo).
- `GESTIONALE_WEB_URL` è l'indirizzo della web app: `http://localhost:4200` sia con `npm start` sia
  con il container web di compose. Finisce nei redirect URI e nei web origins del client.
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

## Client di servizio della console

`gestionale-platform-admin` (realm `gestionale`) è l'unico modo in cui l'API tocca le API
amministrative di Keycloak (ADR 0010): client confidenziale, solo *service account*, ruoli
`view-users`, `query-users`, `manage-users` di `realm-management` (niente *full scope*), scope
`kc-admin-api` (obbligatorio da Keycloak 26.8.2 per le Admin REST API). Il segreto è
`${PLATFORM_KEYCLOAK_CLIENT_SECRET}` dal `.env`. Non può leggere client, ruoli o altri realm.

## Operazioni comuni

Guida passo passo per chi amministra (utenti, ruoli, aziende, licenze):
[docs/gestione-utenti-e-aziende.md](../../docs/gestione-utenti-e-aziende.md).

**Eventi**: il realm registra gli accessi (*Events → User events*, conservati 90 giorni) e le
operazioni degli amministratori (*Admin events*). Nelle installazioni create prima di questa
impostazione vanno attivati da *Realm settings → Events* (il realm si importa solo al primo avvio).

- **Nuovo utente**: Console → realm `gestionale` → Users → Add user; poi nella scheda
  *Attributes* impostare `tenant_id`, in *Role mapping* assegnare `admin`, `operator` o `viewer`,
  in *Credentials* una password temporanea.
- **Nuovo ruolo applicativo**: aggiungerlo nel realm **e** in `Ruolo` (backend), con i permessi;
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

### Pagina di login (tema `gestionale`)

Il realm usa il tema di login `gestionale` (`loginTheme` nel file del realm), in
`themes/gestionale/login`. Eredita da `keycloak.v2` e cambia solo l'aspetto, quindi gli
aggiornamenti di Keycloak continuano ad applicarsi:

- `theme.properties`: tema padre e fogli di stile;
- `resources/css/gestionale.css`: colori (principale `#0B5C8E`, accento giallo `#F2B300` nella linea
  in cima alla scheda), forme e intestazione, tutto tramite variabili `--gestionale-*`;
- `resources/img/logo.svg` e `resources/img/favicon.ico`: logo e icona della scheda (come l'app);
- `resources/js/branding.js`: applica il **marchio dell'installazione** letto da `/config.json` (lo
  stesso della web app: `BRAND_NAME`, `BRAND_LOGO_URL`, `BRAND_PRIMARY_COLOR`, `BRAND_ACCENT_COLOR`):
  nome nell'intestazione e nel titolo, logo, colori. In produzione Keycloak sta sullo stesso dominio
  (`/sso`), quindi la pagina di accesso ha l'aspetto dell'app del cliente senza un tema per cliente;
  senza `config.json` (sviluppo) resta il marchio di prodotto;
- `resources/fonts/`: font Inter incluso (licenza OFL), nessuna richiesta a Google.

Il nome sotto il logo è `displayNameHtml` del realm (sostituito da `branding.js` con il nome
dell'installazione). Il tema va montato (o copiato) in `/opt/keycloak/themes/gestionale`: lo fanno
compose e i comandi `podman run` del README. In produzione si monta l'intera cartella `themes`: il
Deploy sostituisce il tema **sul posto** e riavvia Keycloak quando cambia (Keycloak tiene i temi in
cache); `keycloak-setup` reimposta `loginTheme` e il nome dei realm anche nelle installazioni esistenti.
In sviluppo (`start-dev`) le modifiche a CSS e immagini si vedono ricaricando la pagina;
le modifiche a `theme.properties` o al realm richiedono di ricreare il container.

#### Tema per un cliente

Si crea un tema figlio con il marchio del cliente, senza toccare quello di prodotto
(esempio `gestionale-rossi`):

```
themes/gestionale-rossi/login/
├── theme.properties
└── resources/
    ├── css/cliente.css
    └── img/logo-cliente.svg
```

`theme.properties`:

```
parent=gestionale
import=common/keycloak
styles=css/styles.css css/gestionale.css css/cliente.css
```

`resources/css/cliente.css`:

```css
:root {
    --gestionale-primary: #8B1F3A;
    --gestionale-accent: #E0A100;
    --gestionale-logo-url: url("../img/logo-cliente.svg");
}
```

Poi, nel realm dell'installazione: *Realm settings → Themes → Login theme* = `gestionale-rossi`
e *Realm settings → General → HTML display name* con il nome del cliente
(`<div class="kc-logo-text"><span>Rossi Gestionale</span></div>`).
