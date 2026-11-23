#!/bin/sh
# Eseguito da nginx all'avvio del container (docker-entrypoint.d).
# Genera config.json dalle variabili d'ambiente: la stessa immagine serve installazioni
# e clienti diversi senza ricompilare la web app.
set -eu

: "${OIDC_AUTHORITY:?impostare OIDC_AUTHORITY (es. https://login.example.com/realms/gestionale)}"

cat > /usr/share/nginx/html/config.json <<EOF
{
  "apiUrl": "${WEB_API_URL:-/api}",
  "auth": {
    "authority": "${OIDC_AUTHORITY}",
    "clientId": "${OIDC_CLIENT_ID:-gestionale-web}"
  }
}
EOF
echo "config.json generato per ${OIDC_AUTHORITY}"
