#!/bin/bash
# Allinea Keycloak alla configurazione di prodotto anche nelle installazioni già esistenti
# (il realm si importa solo la prima volta). Idempotente; eseguito dal servizio compose
# "keycloak-setup" a ogni avvio dei servizi.
#
#   - client di servizio "gestionale-platform-admin" nel realm gestionale, con il segreto del .env
#     (PLATFORM_KEYCLOAK_CLIENT_SECRET) e solo i permessi di gestione utenti (ADR 0010);
#   - verifica che il realm "piattaforma" esista (lo importa Keycloak all'avvio, --import-realm).
#
# Usa l'amministratore di Keycloak del .env (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD). Se non è
# più valido, segnala cosa fare a mano ed esce senza errori: l'applicazione funziona comunque.
set -uo pipefail

KC=/opt/keycloak/bin/kcadm.sh
CFG=/tmp/kcadm.config
SERVER="${KC_SERVER:-http://keycloak:8080/sso}"
CLIENT=gestionale-platform-admin

if [ -z "${PLATFORM_KEYCLOAK_CLIENT_SECRET:-}" ]; then
  echo "PLATFORM_KEYCLOAK_CLIENT_SECRET mancante nel .env: lancia il workflow Prepara server." >&2
  exit 0
fi

echo "Attendo Keycloak su $SERVER…"
logged=false
for _ in $(seq 1 60); do
  if "$KC" config credentials --config "$CFG" --server "$SERVER" --realm master \
       --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" > /dev/null 2>&1; then
    logged=true
    break
  fi
  sleep 5
done
if [ "$logged" != true ]; then
  echo "ATTENZIONE: accesso amministrativo a Keycloak non riuscito (KEYCLOAK_ADMIN nel .env)." >&2
  echo "Il client $CLIENT va creato a mano: docs/console-piattaforma.md, «Installazioni esistenti»." >&2
  exit 0
fi

id=$("$KC" get clients --config "$CFG" -r gestionale -q clientId="$CLIENT" --fields id --format csv --noquotes 2> /dev/null | head -n 1)
if [ -z "$id" ]; then
  echo "Creo il client $CLIENT nel realm gestionale."
  "$KC" create clients --config "$CFG" -r gestionale -f - > /dev/null <<JSON
{
  "clientId": "$CLIENT",
  "name": "civil beefy · servizio della console di piattaforma",
  "enabled": true,
  "publicClient": false,
  "clientAuthenticatorType": "client-secret",
  "protocol": "openid-connect",
  "standardFlowEnabled": false,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": true,
  "frontchannelLogout": false,
  "fullScopeAllowed": false,
  "defaultClientScopes": ["basic", "roles"]
}
JSON
  id=$("$KC" get clients --config "$CFG" -r gestionale -q clientId="$CLIENT" --fields id --format csv --noquotes | head -n 1)
fi

# Il segreto passa da stdin (non dalla riga di comando) ed è sempre quello del .env.
printf '{"secret": "%s", "serviceAccountsEnabled": true, "publicClient": false}' "$PLATFORM_KEYCLOAK_CLIENT_SECRET" |
  "$KC" update "clients/$id" --config "$CFG" -r gestionale -f - > /dev/null

ROLES=(view-users query-users manage-users)
# Ruoli del service account: solo gestione utenti del realm delle aziende.
role_args=()
for role in "${ROLES[@]}"; do role_args+=(--rolename "$role"); done
"$KC" add-roles --config "$CFG" -r gestionale --uusername "service-account-$CLIENT" \
  --cclientid realm-management "${role_args[@]}" > /dev/null

# Gli stessi ruoli nel token (scope del client: niente "full scope").
rm_id=$("$KC" get clients --config "$CFG" -r gestionale -q clientId=realm-management --fields id --format csv --noquotes | head -n 1)
roles_json=$(for role in "${ROLES[@]}"; do "$KC" get "clients/$rm_id/roles/$role" --config "$CFG" -r gestionale; done |
  tr -d '\n' | sed 's/}[[:space:]]*{/},{/g')
printf '[%s]' "$roles_json" |
  "$KC" create "clients/$id/scope-mappings/clients/$rm_id" --config "$CFG" -r gestionale -f - > /dev/null

# Keycloak 26.8.2+: le Admin REST API accettano solo token con lo scope kc-admin-api.
# (l'immagine di Keycloak non ha awk: solo bash)
scope_id=""
while IFS=, read -r sid sname; do
  if [ "$sname" = "kc-admin-api" ]; then scope_id="$sid"; fi
done < <("$KC" get client-scopes --config "$CFG" -r gestionale --fields id,name --format csv --noquotes)
if [ -n "$scope_id" ]; then
  "$KC" update "clients/$id/default-client-scopes/$scope_id" --config "$CFG" -r gestionale > /dev/null
fi
echo "Client $CLIENT pronto (solo gestione utenti del realm gestionale)."

if "$KC" get realms/piattaforma --config "$CFG" --fields realm > /dev/null 2>&1; then
  echo "Realm piattaforma presente."
else
  echo "ATTENZIONE: realm piattaforma assente: riavvia Keycloak (docker compose restart keycloak) per importarlo." >&2
fi
rm -f "$CFG"
