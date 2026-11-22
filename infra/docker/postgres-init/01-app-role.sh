#!/bin/sh
# Eseguito da postgres solo alla prima inizializzazione del volume.
# Crea il ruolo con cui si collega l'applicazione: non superuser, non proprietario
# delle tabelle, senza BYPASSRLS. I privilegi sulle tabelle li concede la migrazione V1.
set -eu

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$DB_USER" -v app_password="$DB_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
SQL
