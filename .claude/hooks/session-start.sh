#!/usr/bin/env bash
# Hook SessionStart: stampa il contesto minimo di una sessione di lavoro (PR 0.6 della roadmap).
#
# Quello che stampa finisce nel contesto del modello a ogni avvio di sessione: resta quindi
# deliberatamente sotto le ~10 righe. Niente installazioni, niente fetch, niente scritture:
# solo lettura di git, docs/roadmap.md e della cartella delle migrazioni.
#
# Prova a mano:  echo '{"source":"startup"}' | .claude/hooks/session-start.sh
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# --- 0. su quali avvii parlare ----------------------------------------------
# startup/clear/compact: il contesto è vuoto o è stato riassunto, il promemoria serve.
# resume: la sessione riprende con il suo contesto, ripeterlo è solo spesa.
sorgente=""
if [ ! -t 0 ]; then
  ingresso="$(timeout 5 cat || true)"
  if command -v jq > /dev/null 2>&1 && [ -n "$ingresso" ]; then
    sorgente="$(printf '%s' "$ingresso" | jq -r '.source // ""' 2> /dev/null || true)"
  fi
fi
[ "$sorgente" = "resume" ] && exit 0

accorcia() { # testo, lunghezza massima: taglia solo su uno spazio, mai dentro un carattere UTF-8
  sed -e 's/\[\([^]]*\)\]([^)]*)/\1/g' -e 's/[*`]//g' <<< "$1" \
    | awk -v massimo="$2" '{
        if (length($0) <= massimo) { print; next }
        taglio = 0
        for (i = 1; i <= massimo; i++) if (substr($0, i, 1) == " ") taglio = i
        print (taglio ? substr($0, 1, taglio - 1) " …" : $0)
      }'
}

echo "== Civil Beefy: contesto di sessione =="

# --- 1. branch e scostamento da main ----------------------------------------
branch="$(git branch --show-current 2> /dev/null || true)"
if [ -z "$branch" ]; then
  echo "Git: nessun branch (checkout staccato o repository assente)"
else
  posizione=""
  if git rev-parse --verify --quiet origin/main > /dev/null 2>&1; then
    if conteggio="$(git rev-list --left-right --count origin/main...HEAD 2> /dev/null)"; then
      posizione=" — +$(cut -f2 <<< "$conteggio") commit su origin/main, $(cut -f1 <<< "$conteggio") da riprendere"
    fi
  fi
  sporchi="$(git status --porcelain 2> /dev/null | wc -l | tr -d ' ')"
  [ "$sporchi" != "0" ] && posizione="$posizione — $sporchi file non committati"
  echo "Branch: $branch$posizione"
fi

# --- 2. pull request aperta --------------------------------------------------
# Solo lettura via REST (la GraphQL non è disponibile in sessione). Senza rete o senza gh
# la riga dice semplicemente che non si sa.
repo="$(git remote get-url origin 2> /dev/null | sed -e 's#^git@[^:]*:##' -e 's#^https\?://[^/]*/##' -e 's/\.git$//' || true)"
if [ -z "$branch" ] || [ -z "$repo" ] || ! command -v gh > /dev/null 2>&1; then
  echo "PR: non verificata"
else
  pr="$(timeout 15 gh api "repos/$repo/pulls?state=open&per_page=100" \
    --jq ".[] | select(.head.ref == \"$branch\") | \"#\(.number)\(if .draft then \" (bozza)\" else \"\" end) \(.title)\"" \
    2> /dev/null | head -1 || true)"
  if [ -n "$pr" ]; then
    echo "PR: $(accorcia "$pr" 100)"
  else
    altre="$(timeout 15 gh api "repos/$repo/pulls?state=open&per_page=100" --jq 'length' 2> /dev/null || true)"
    case "$altre" in
      '') echo "PR: non verificata (GitHub non raggiungibile)" ;;
      0) echo "PR: nessuna aperta sul repository" ;;
      *) echo "PR: nessuna per questo branch ($altre aperte altrove: attenzione ai file condivisi)" ;;
    esac
  fi
fi

# --- 3. prima voce di roadmap non completata ---------------------------------
# Righe della tabella: | 1.4 | contenuto | V7 | ⬜ |. Si salta ✅ (fatta) e ⏸️ (attesa esterna).
if [ -r docs/roadmap.md ]; then
  voci="$(awk -F'|' '
    NF >= 5 {
      pr = $2; testo = $3; stato = $5
      gsub(/^[ \t]+|[ \t]+$/, "", pr); gsub(/^[ \t]+|[ \t]+$/, "", testo); gsub(/^[ \t]+|[ \t]+$/, "", stato)
      if (pr !~ /^[0-9]+\.[0-9]+$/) next
      if (stato == "🟨" && corso == "") corso = pr " 🟨 " testo
      if (stato == "⬜" && fare == "") fare = pr " ⬜ " testo
    }
    END { if (corso != "") print corso; if (fare != "") print fare }
  ' docs/roadmap.md)"
  if [ -z "$voci" ]; then
    echo "Roadmap: nessuna voce da fare in docs/roadmap.md"
  else
    while IFS= read -r voce; do
      [ -n "$voce" ] && echo "Roadmap: $(accorcia "$voce" 95)"
    done <<< "$voci"
  fi
fi

# --- 4. primo numero Flyway libero (ADR 0013) -------------------------------
# Il numero è definitivo solo al merge: libero = oltre il massimo sia locale sia su origin/main.
cartella="apps/api/src/main/resources/db/migration"
if [ -d "$cartella" ]; then
  massimo_di() { sed -n 's#.*/\?V\([0-9]\+\)__.*\.sql$#\1#p' | sort -n | tail -1; }
  locale="$(find "$cartella" -maxdepth 1 -name 'V*.sql' 2> /dev/null | massimo_di)"
  remoto=""
  git rev-parse --verify --quiet origin/main > /dev/null 2>&1 &&
    remoto="$(git ls-tree -r --name-only origin/main -- "$cartella" 2> /dev/null | massimo_di)"
  massimo="$(printf '%s\n' "${locale:-0}" "${remoto:-0}" | sort -n | tail -1)"
  echo "Flyway: prossimo libero V$((massimo + 1)) (massimo attuale V$massimo, ADR 0013)"
fi

echo "Roadmap = fonte di verità: aggiorna la voce nella stessa PR. Ricettario e regole: CLAUDE.md"
