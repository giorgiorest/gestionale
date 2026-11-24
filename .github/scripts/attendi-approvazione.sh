#!/usr/bin/env bash
# Approvazione del deploy dentro il rilascio, senza i Required reviewers di GitHub (che nei
# repository privati richiedono GitHub Enterprise). Usato dal job "approvazione" di release-images.yml.
#
# Apre un'issue «Rilascio pronto», chiude quelle dei rilasci precedenti ancora aperte e aspetta un
# commento /deploy (approva) o /annulla (rifiuta) da chi ha accesso in scrittura al repository
# (author_association OWNER, MEMBER o COLLABORATOR). Scrive approved=true|false in GITHUB_OUTPUT.
#
# Variabili: GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_OUTPUT, GITHUB_STEP_SUMMARY,
#            OWNER, RUN_URL, DEPLOY_URL, WAIT_MINUTES (default 30), POLL_SECONDS (default 15).
set -euo pipefail

wait_minutes="${WAIT_MINUTES:-30}"
poll="${POLL_SECONDS:-15}"
short="${GITHUB_SHA:0:7}"
title_prefix="Rilascio pronto:"

# Le richieste dei rilasci precedenti non valgono più: si installa sempre l'ultima versione.
gh issue list -R "$GITHUB_REPOSITORY" --state open --search "\"$title_prefix\" in:title" --json number --jq '.[].number' |
  while read -r old; do
    gh issue close "$old" -R "$GITHUB_REPOSITORY" --comment "Superato dal rilascio \`$short\`." > /dev/null || true
  done

body="Le immagini di \`sha-$GITHUB_SHA\` sono pubblicate. @$OWNER, vuoi installarle in produzione?

- commenta **\`/deploy\`** per installare;
- commenta **\`/annulla\`** per lasciare la produzione com'è.

Conta solo il commento di chi ha accesso in scrittura al repository. Senza risposta entro **$wait_minutes minuti** la richiesta scade: potrai sempre installare con [Deploy → Run workflow]($DEPLOY_URL).

[Rilascio in corso]($RUN_URL)"
url=$(gh issue create -R "$GITHUB_REPOSITORY" --title "$title_prefix $short — commenta /deploy per installare" --body "$body")
number="${url##*/}"
{
  echo "## In attesa di approvazione"
  echo
  echo "Commenta \`/deploy\` su [questa issue]($url) per installare \`$short\` (scade tra $wait_minutes minuti)."
} >> "$GITHUB_STEP_SUMMARY"
echo "::notice::In attesa di approvazione: commenta /deploy su $url"

decision=""
approver=""
deadline=$(( $(date +%s) + wait_minutes * 60 ))
while [ -z "$decision" ] && [ "$(date +%s)" -lt "$deadline" ]; do
  sleep "$poll"
  while IFS=$'\t' read -r login association text; do
    case "$association" in OWNER | MEMBER | COLLABORATOR) ;; *) continue ;; esac
    case "$text" in
      /deploy | /approva) decision=approve ;;
      /annulla | /rifiuta) decision=reject ;;
      *) continue ;;
    esac
    approver="$login"
    break
  done < <(gh api "repos/$GITHUB_REPOSITORY/issues/$number/comments" --paginate \
    --jq '.[] | select(.user.type != "Bot") | [.user.login, .author_association, (.body | ascii_downcase | gsub("\\s+"; ""))] | @tsv')
done

case "$decision" in
  approve)
    gh issue close "$number" -R "$GITHUB_REPOSITORY" --comment "Approvato da @$approver: installazione di \`$short\` in corso ([rilascio]($RUN_URL))." > /dev/null
    echo "approved=true" >> "$GITHUB_OUTPUT"
    echo "Approvato da **$approver**: parte il deploy." >> "$GITHUB_STEP_SUMMARY"
    ;;
  reject)
    gh issue close "$number" -R "$GITHUB_REPOSITORY" --comment "Annullato da @$approver: la produzione resta com'è." > /dev/null
    echo "approved=false" >> "$GITHUB_OUTPUT"
    echo "Annullato da **$approver**." >> "$GITHUB_STEP_SUMMARY"
    ;;
  *)
    gh issue close "$number" -R "$GITHUB_REPOSITORY" --comment "Nessuna risposta entro $wait_minutes minuti. Per installare questa versione: [Deploy → Run workflow]($DEPLOY_URL)." > /dev/null
    echo "approved=false" >> "$GITHUB_OUTPUT"
    echo "::warning::Approvazione scaduta: per installare avvia Deploy a mano."
    echo "Approvazione scaduta: [Deploy → Run workflow]($DEPLOY_URL)." >> "$GITHUB_STEP_SUMMARY"
    ;;
esac
