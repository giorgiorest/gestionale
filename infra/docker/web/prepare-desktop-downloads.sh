#!/bin/sh
# Prepara i pacchetti dell'app per la pagina di download della web app (/app-desktop).
# Uso: prepare-desktop-downloads.sh <cartella installer CI> <cartella destinazione> <commit> [cartella APK]
#
# Dalla cartella dell'artifact della CI Desktop (msi/*.msi e nsis/*-setup.exe) copia:
#   civilbeefy-setup.exe  installazione per l'utente, senza permessi di amministratore
#   civilbeefy.msi        installazione centralizzata (reparto IT, criteri di gruppo)
# e, se è indicata la cartella dell'artifact di CI Android:
#   civilbeefy.apk        app Android, da installare a mano (fuori dal Play Store)
#
# Scrive desktop.json (versione, commit, data, dimensione e SHA-256 di ogni file), letto dalla
# pagina /app-desktop. L'APK è facoltativo: se la build Android fallisce la pagina mostra solo
# Windows, invece di non mostrare niente.
set -eu

src="$1"
dest="$2"
commit="$3"
apk_src="${4:-}"

setup=$(find "$src" -type f -name '*-setup.exe' | head -n 1)
msi=$(find "$src" -type f -name '*.msi' | head -n 1)
if [ -z "$setup" ] || [ -z "$msi" ]; then
  echo "Installer non trovati in $src" >&2
  exit 1
fi

mkdir -p "$dest"
cp "$setup" "$dest/civilbeefy-setup.exe"
cp "$msi" "$dest/civilbeefy.msi"

apk=''
if [ -n "$apk_src" ] && [ -d "$apk_src" ]; then
  apk=$(find "$apk_src" -type f -name '*.apk' ! -name '*-unsigned.apk' | head -n 1)
fi
if [ -n "$apk" ]; then
  cp "$apk" "$dest/civilbeefy.apk"
else
  echo "APK non fornito: la pagina di download mostrerà solo Windows" >&2
fi

# Versione dal nome del file generato da Tauri (es. "civil beefy_0.1.0_x64-setup.exe").
version=$(basename "$setup" | sed -n 's/^.*_\([0-9][0-9.]*\)_.*$/\1/p')

entry() {
  file="$1"
  size=$(wc -c < "$dest/$file" | tr -d ' ')
  sha=$(sha256sum "$dest/$file" | cut -d ' ' -f 1)
  printf '{ "file": "%s", "size": %s, "sha256": "%s" }' "$file" "$size" "$sha"
}

{
  echo '{'
  echo "  \"version\": \"${version:-sconosciuta}\","
  echo "  \"commit\": \"$commit\","
  echo "  \"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
  echo "  \"setup\": $(entry civilbeefy-setup.exe),"
  if [ -n "$apk" ]; then
    echo "  \"msi\": $(entry civilbeefy.msi),"
    echo "  \"android\": $(entry civilbeefy.apk)"
  else
    echo "  \"msi\": $(entry civilbeefy.msi)"
  fi
  echo '}'
} > "$dest/desktop.json"

echo "Pacchetti pronti in $dest (versione ${version:-sconosciuta}${apk:+, APK incluso})"
