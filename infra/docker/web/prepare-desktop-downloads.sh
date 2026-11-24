#!/bin/sh
# Prepara gli installer dell'app desktop per la pagina di download della web app.
# Uso: prepare-desktop-downloads.sh <cartella installer CI> <cartella destinazione> <commit>
#
# Dalla cartella dell'artifact della CI Desktop (msi/*.msi e nsis/*-setup.exe) copia:
#   civilbeefy-setup.exe  installazione per l'utente, senza permessi di amministratore
#   civilbeefy.msi        installazione centralizzata (reparto IT, criteri di gruppo)
# e scrive desktop.json (versione, commit, data, dimensione e SHA-256), letto dalla pagina /app-desktop.
set -eu

src="$1"
dest="$2"
commit="$3"

setup=$(find "$src" -type f -name '*-setup.exe' | head -n 1)
msi=$(find "$src" -type f -name '*.msi' | head -n 1)
if [ -z "$setup" ] || [ -z "$msi" ]; then
  echo "Installer non trovati in $src" >&2
  exit 1
fi

mkdir -p "$dest"
cp "$setup" "$dest/civilbeefy-setup.exe"
cp "$msi" "$dest/civilbeefy.msi"

# Versione dal nome del file generato da Tauri (es. "Civil Beefy_0.1.0_x64-setup.exe").
version=$(basename "$setup" | sed -n 's/^.*_\([0-9][0-9.]*\)_.*$/\1/p')

entry() {
  file="$1"
  size=$(wc -c < "$dest/$file" | tr -d ' ')
  sha=$(sha256sum "$dest/$file" | cut -d ' ' -f 1)
  printf '{ "file": "%s", "size": %s, "sha256": "%s" }' "$file" "$size" "$sha"
}

cat > "$dest/desktop.json" <<JSON
{
  "version": "${version:-sconosciuta}",
  "commit": "$commit",
  "date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "setup": $(entry civilbeefy-setup.exe),
  "msi": $(entry civilbeefy.msi)
}
JSON
echo "Installer pronti in $dest (versione ${version:-sconosciuta})"
