#!/bin/bash
# Prepara un server Ubuntu (VPS: Aruba, OVH, Hetzner…) per Civil Beefy. Idempotente: si può rilanciare.
# Non per le immagini Oracle Cloud, che hanno regole iptables proprie (docs/deploy-oracle-cloud.md).
# Lo esegue il workflow "Prepara server" via SSH, come root (sudo):
#
#   prepare-server.sh <cartella app> <dominio> <email ACME> <registry immagini> <utente>
#                    <S3 endpoint> <S3 region> <S3 bucket> <S3 access key> <S3 secret key> <S3 path style>
#
# Cosa fa (docs/deploy-vps.md, passi 4–7 e backup):
#   1. aggiornamenti di sistema e aggiornamenti di sicurezza automatici;
#   2. fuso orario Europe/Rome;
#   3. firewall ufw: solo SSH, HTTP e HTTPS (anche HTTP/3);
#   4. SSH solo con chiave, niente accesso di root;
#   5. Docker e Docker Compose, utente nel gruppo docker;
#   6. cartella dell'applicazione e .env con password casuali, SOLO se il .env non esiste;
#      in un .env esistente aggiorna le configurazioni S3 ricevute dal workflow, senza toccare le altre;
#   7. backup giornaliero del database alle 3, 14 giorni di storico.
# Non stampa mai password o segreti.
set -euo pipefail

app_dir="$1"
domain="$2"
acme_email="$3"
registry="$4"
app_user="$5"
s3_endpoint="${6:-}"
s3_region="${7:-us-east-1}"
s3_bucket="${8:-}"
s3_access_key="${9:-}"
s3_secret_key="${10:-}"
s3_force_path_style="${11:-false}"

export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
# Attende gli aggiornamenti automatici già in corso (macchina appena creata) invece di fallire.
apt_get() {
  apt-get -o DPkg::Lock::Timeout=1800 -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold "$@"
}
group() { echo "::group::$*"; }
endgroup() { echo "::endgroup::"; }

id "$app_user" > /dev/null 2>&1 || { echo "::error::L'utente $app_user non esiste sulla macchina."; exit 1; }

group "1. Aggiornamenti di sistema"
apt_get update -q
apt_get upgrade -y -q
apt_get install -y -q unattended-upgrades ufw curl ca-certificates openssl cron
systemctl enable --now unattended-upgrades cron > /dev/null 2>&1 || true
endgroup

group "2. Fuso orario"
timedatectl set-timezone Europe/Rome || echo "::warning::Fuso orario non impostato."
endgroup

group "3. Firewall"
ufw allow OpenSSH > /dev/null
ufw allow 80/tcp > /dev/null
ufw allow 443/tcp > /dev/null
ufw allow 443/udp > /dev/null
ufw --force enable > /dev/null
ufw status
endgroup

group "4. SSH solo con chiave"
# 01-: sshd usa il PRIMO valore trovato e legge sshd_config.d in ordine alfabetico; un file
# 50-cloud-init.conf con "PasswordAuthentication yes" (frequente sui VPS) vincerebbe su un 99-.
cat > /etc/ssh/sshd_config.d/01-civilbeefy.conf <<'EOF'
# Civil Beefy: accesso solo con chiave, niente root (workflow Prepara server)
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
rm -f /etc/ssh/sshd_config.d/99-civilbeefy.conf
sshd -t
systemctl reload ssh 2> /dev/null || systemctl restart ssh
sshd -T | grep -Ei '^(passwordauthentication|permitrootlogin) '
endgroup

group "5. Docker"
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
  sh /tmp/get-docker.sh
fi
systemctl enable --now docker
getent group docker > /dev/null || groupadd docker
usermod -aG docker "$app_user"
docker --version
docker compose version
endgroup

group "6. Cartella dell'applicazione e .env"
install -d -o "$app_user" -g "$app_user" "$app_dir" "$app_dir/backup"
env_file="$app_dir/.env"
if [ -f "$env_file" ]; then
  echo "Il .env esiste già: i valori presenti non vengono modificati, tranne le configurazioni S3 ricevute."
  grep -qx "APP_DOMAIN=$domain" "$env_file" ||
    echo "::warning::APP_DOMAIN nel .env non è $domain: correggilo a mano (nano $env_file)."
else
  gen() { openssl rand -hex 24; }
  umask 077
  cat > "$env_file" <<EOF
# Creato dal workflow "Prepara server" il $(date -u +%Y-%m-%d). Significato delle voci:
# infra/deploy/oracle/.env.example. Dopo una modifica: docker compose up -d
APP_DOMAIN=$domain
ACME_EMAIL=$acme_email
IMAGE_REGISTRY=$registry
IMAGE_TAG=latest
POSTGRES_USER=gestionale
POSTGRES_PASSWORD=$(gen)
DB_PASSWORD=$(gen)
KEYCLOAK_DB_PASSWORD=$(gen)
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$(gen)
DEMO_ENABLED=true
EMAIL_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM=Civil Beefy <noreply@example.com>
BRAND_NAME=
BRAND_LOGO_URL=
BRAND_PRIMARY_COLOR=
BRAND_ACCENT_COLOR=
LEGAL_PRIVACY_URL=
LEGAL_TERMS_URL=
S3_ENDPOINT=$s3_endpoint
S3_REGION=$s3_region
S3_BUCKET=$s3_bucket
S3_ACCESS_KEY=$s3_access_key
S3_SECRET_KEY=$s3_secret_key
S3_FORCE_PATH_STYLE=$s3_force_path_style
EOF
  chown "$app_user:$app_user" "$env_file"
  chmod 600 "$env_file"
  echo "Creato $env_file con password casuali (leggi la password di Keycloak sulla macchina: grep KEYCLOAK_ADMIN_PASSWORD $env_file)."
fi
# Voci introdotte dopo la prima installazione: aggiunte solo se mancano (mai sovrascritte).
ensure_env() {
  if ! grep -q "^$1=" "$env_file"; then
    printf '%s=%s\n' "$1" "$2" >> "$env_file"
    echo "Aggiunta al .env la voce $1."
  fi
}
ensure_env PLATFORM_KEYCLOAK_CLIENT_SECRET "$(openssl rand -hex 32)"
ensure_env ADMIN_ALLOWED_IPS ""
ensure_env S3_ENDPOINT "$s3_endpoint"
ensure_env S3_REGION "$s3_region"
ensure_env S3_BUCKET "$s3_bucket"
ensure_env S3_ACCESS_KEY "$s3_access_key"
ensure_env S3_SECRET_KEY "$s3_secret_key"
ensure_env S3_FORCE_PATH_STYLE "$s3_force_path_style"

# Per le voci S3 il valore ricevuto dal workflow è autoritativo; così il rinnovo di una secret
# viene applicato alle installazioni esistenti senza modificare le altre impostazioni del .env.
set_env() {
  local name="$1"
  local value="$2"
  if grep -q "^${name}=" "$env_file"; then
    sed -i "s|^${name}=.*|${name}=${value}|" "$env_file"
  else
    printf '%s=%s\n' "$name" "$value" >> "$env_file"
  fi
}
set_env S3_ENDPOINT "$s3_endpoint"
set_env S3_REGION "$s3_region"
set_env S3_BUCKET "$s3_bucket"
set_env S3_ACCESS_KEY "$s3_access_key"
set_env S3_SECRET_KEY "$s3_secret_key"
set_env S3_FORCE_PATH_STYLE "$s3_force_path_style"
endgroup

group "7. Backup giornaliero del database"
cat > /etc/cron.d/civilbeefy-backup <<EOF
# Civil Beefy: backup del database (applicazione e Keycloak) alle 3, 14 giorni di storico.
0 3 * * * $app_user cd $app_dir && [ -f compose.yml ] && docker compose exec -T db sh -c 'pg_dumpall -U "\$POSTGRES_USER"' | gzip > backup/db-\$(date +\%F).sql.gz && find backup -name 'db-*.sql.gz' -mtime +14 -delete
EOF
chmod 644 /etc/cron.d/civilbeefy-backup
echo "Backup in $app_dir/backup (copia anche fuori dalla macchina: docs/deploy-vps.md)."
endgroup

if [ -f /var/run/reboot-required ]; then
  echo "reboot-required"
fi
echo "Server pronto."
