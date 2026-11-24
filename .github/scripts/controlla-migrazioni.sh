#!/usr/bin/env bash
# Controlla la numerazione delle migrazioni Flyway (ADR 0013).
#
# Verifica tre cose, in modo che l'errore emerga nella pull request e non in produzione:
#   1. ogni file rispetta il formato V<numero>__descrizione.sql (minuscole e trattini bassi);
#   2. non ci sono due migrazioni con lo stesso numero di versione;
#   3. ogni migrazione nuova rispetto al ramo di riferimento ha una versione MAGGIORE della più
#      alta già presente su quel ramo. Flyway, con out-of-order disattivato, rifiuta una versione
#      inferiore a una già applicata: senza questo controllo il deploy si fermerebbe in produzione.
#
# Uso: .github/scripts/controlla-migrazioni.sh [ramo-di-riferimento]   (default: origin/main)
# Richiede che il ramo di riferimento sia disponibile in locale (in CI: fetch-depth 0).
set -euo pipefail

cartella="apps/api/src/main/resources/db/migration"
riferimento="${1:-origin/main}"
errori=0

errore() {
  echo "::error::$*"
  errori=$((errori + 1))
}

if [ ! -d "$cartella" ]; then
  errore "Cartella delle migrazioni non trovata: $cartella"
  exit 1
fi

# --- 1. formato dei nomi -----------------------------------------------------
nomi=()
while IFS= read -r percorso; do
  nomi+=("$(basename "$percorso")")
done < <(find "$cartella" -maxdepth 1 -name '*.sql' | sort)

if [ ${#nomi[@]} -eq 0 ]; then
  errore "Nessuna migrazione trovata in $cartella"
  exit 1
fi

for nome in "${nomi[@]}"; do
  if ! [[ "$nome" =~ ^V[0-9]+__[a-z0-9_]+\.sql$ ]]; then
    errore "Nome non valido: $nome (atteso V<numero>__descrizione.sql, minuscole e trattini bassi)"
  fi
done

if [ "$errori" -gt 0 ]; then
  echo "Correggi i nomi prima di proseguire. Vedi docs/decisioni/0013-numerazione-migrazioni.md"
  exit 1
fi

versione_di() {
  # V12__nome.sql -> 12
  echo "${1#V}" | cut -d_ -f1
}

# --- 2. versioni duplicate ---------------------------------------------------
versioni=()
for nome in "${nomi[@]}"; do
  versioni+=("$(versione_di "$nome")")
done

duplicate="$(printf '%s\n' "${versioni[@]}" | sort -n | uniq -d)"
if [ -n "$duplicate" ]; then
  while IFS= read -r v; do
    errore "Versione duplicata V$v: $(printf '%s\n' "${nomi[@]}" | grep "^V${v}__")"
  done <<< "$duplicate"
fi

# --- 3. le migrazioni nuove vengono dopo quelle del ramo di riferimento ------
if ! git rev-parse --verify --quiet "$riferimento" > /dev/null; then
  echo "::notice::Ramo di riferimento '$riferimento' non disponibile: salto il confronto."
  [ "$errori" -eq 0 ] && echo "Migrazioni: formato e numerazione locali in ordine." || exit 1
  exit 0
fi

nomi_riferimento=()
while IFS= read -r percorso; do
  [ -n "$percorso" ] || continue
  nomi_riferimento+=("$(basename "$percorso")")
done < <(git ls-tree -r --name-only "$riferimento" -- "$cartella" | grep '\.sql$' || true)

massimo=0
for nome in "${nomi_riferimento[@]:-}"; do
  [ -n "$nome" ] || continue
  v="$(versione_di "$nome")"
  [ "$v" -gt "$massimo" ] && massimo="$v"
done

for nome in "${nomi[@]}"; do
  gia_presente=false
  for esistente in "${nomi_riferimento[@]:-}"; do
    [ "$nome" = "$esistente" ] && gia_presente=true && break
  done
  $gia_presente && continue

  v="$(versione_di "$nome")"
  if [ "$v" -le "$massimo" ]; then
    errore "La migrazione nuova $nome ha versione V$v, non superiore alla più alta su $riferimento (V$massimo). Rinominala in V$((massimo + 1))__… o successiva (ADR 0013)."
  fi
done

if [ "$errori" -gt 0 ]; then
  echo "Controllo delle migrazioni non superato ($errori). Vedi docs/decisioni/0013-numerazione-migrazioni.md"
  exit 1
fi

echo "Migrazioni in ordine (${#nomi[@]} file, massima su $riferimento: V$massimo)."
