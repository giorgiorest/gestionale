#!/bin/sh
# Eseguito da nginx all'avvio del container (docker-entrypoint.d).
# Genera config.json dalle variabili d'ambiente: la stessa immagine serve installazioni
# e clienti diversi senza ricompilare la web app.
#
# Obbligatoria: OIDC_AUTHORITY
# Facoltative:  WEB_API_URL, OIDC_CLIENT_ID, OIDC_DESKTOP_CLIENT_ID,
#               BRAND_NAME, BRAND_LOGO_URL, BRAND_PRIMARY_COLOR e BRAND_ACCENT_COLOR (#RRGGBB),
#               LEGAL_PRIVACY_URL e LEGAL_TERMS_URL (informativa privacy e condizioni d'uso)
set -eu

: "${OIDC_AUTHORITY:?impostare OIDC_AUTHORITY (es. https://login.example.com/realms/gestionale)}"

for var in BRAND_PRIMARY_COLOR BRAND_ACCENT_COLOR; do
  eval "value=\${$var:-}"
  if [ -n "$value" ] && ! echo "$value" | grep -Eq '^#[0-9A-Fa-f]{6}$'; then
    echo "$var non valido (atteso #RRGGBB): $value" >&2
    exit 1
  fi
done

# Le virgolette e le barre inverse romperebbero il JSON generato.
for var in BRAND_NAME BRAND_LOGO_URL LEGAL_PRIVACY_URL LEGAL_TERMS_URL; do
  eval "value=\${$var:-}"
  case "$value" in
    *\"* | *\\*)
      echo "$var non può contenere virgolette o barre inverse: $value" >&2
      exit 1
      ;;
  esac
done

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
add_brand accentColor "${BRAND_ACCENT_COLOR:-}"

# Documenti legali del titolare (pagina Privacy e cookie).
legal=""
add_legal() {
  if [ -n "$2" ]; then
    [ -n "$legal" ] && legal="$legal,"
    legal="$legal
    \"$1\": \"$2\""
  fi
}
add_legal privacyUrl "${LEGAL_PRIVACY_URL:-}"
add_legal termsUrl "${LEGAL_TERMS_URL:-}"

cat > /usr/share/nginx/html/config.json <<EOF
{
  "apiUrl": "${WEB_API_URL:-/api}",
  "auth": {
    "authority": "${OIDC_AUTHORITY}",
    "clientId": "${OIDC_CLIENT_ID:-gestionale-web}",
    "desktopClientId": "${OIDC_DESKTOP_CLIENT_ID:-gestionale-desktop}"
  },
  "branding": {${branding}
  },
  "legal": {${legal}
  }
}
EOF
echo "config.json generato per ${OIDC_AUTHORITY}"
