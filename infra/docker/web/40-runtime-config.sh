#!/bin/sh
# Eseguito da nginx all'avvio del container (docker-entrypoint.d).
# Genera config.json dalle variabili d'ambiente: la stessa immagine serve installazioni
# e clienti diversi senza ricompilare la web app.
#
# Obbligatoria: OIDC_AUTHORITY
# Facoltative:  WEB_API_URL, OIDC_CLIENT_ID, OIDC_DESKTOP_CLIENT_ID,
#               BRAND_NAME, BRAND_LOGO_URL, BRAND_PRIMARY_COLOR (#RRGGBB)
set -eu

: "${OIDC_AUTHORITY:?impostare OIDC_AUTHORITY (es. https://login.example.com/realms/gestionale)}"

if [ -n "${BRAND_PRIMARY_COLOR:-}" ] && ! echo "$BRAND_PRIMARY_COLOR" | grep -Eq '^#[0-9A-Fa-f]{6}$'; then
  echo "BRAND_PRIMARY_COLOR non valido (atteso #RRGGBB): $BRAND_PRIMARY_COLOR" >&2
  exit 1
fi

# Marchio: solo i campi indicati (senza valori l'app usa quello di prodotto).
branding=""
add_brand() {
  if [ -n "$2" ]; then
    [ -n "$branding" ] && branding="$branding,"
    branding="$branding
    \"$1\": \"$2\""
  fi
}
add_brand name "${BRAND_NAME:-}"
add_brand logoUrl "${BRAND_LOGO_URL:-}"
add_brand primaryColor "${BRAND_PRIMARY_COLOR:-}"

cat > /usr/share/nginx/html/config.json <<EOF
{
  "apiUrl": "${WEB_API_URL:-/api}",
  "auth": {
    "authority": "${OIDC_AUTHORITY}",
    "clientId": "${OIDC_CLIENT_ID:-gestionale-web}",
    "desktopClientId": "${OIDC_DESKTOP_CLIENT_ID:-gestionale-desktop}"
  },
  "branding": {${branding}
  }
}
EOF
echo "config.json generato per ${OIDC_AUTHORITY}"
