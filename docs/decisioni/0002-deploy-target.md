# ADR 0002 — Target di deploy

- **Stato**: aperta
- **Data**: 2026-11-22

## Contesto

Il workflow `.github/workflows/deploy.yml` è un placeholder manuale che fallisce di proposito,
per evitare deploy accidentali. La CI pubblica già l'immagine dell'API su GHCR
(`ghcr.io/<owner>/gestionale-api`) ad ogni push su `main`.

<!--
  DOMANDE APERTE da chiudere prima di implementare il deploy:

  1. Provider: dove girano API, database e web?
     (es. VM su Hetzner/OVH/Aruba, AWS/Azure/GCP, PaaS come Fly.io/Render, Kubernetes gestito)
     Vincoli: dati in UE? requisiti GDPR/contrattuali dei clienti?
  2. Database: PostgreSQL gestito (backup, PITR, repliche) o installato su VM?
  3. Metodo: docker compose via SSH, Kubernetes/Helm, servizio PaaS, Terraform per l'infrastruttura?
  4. Web: servita dallo stesso nginx dell'API o da CDN/object storage?
  5. Segreti necessari (da creare come GitHub Environment secrets, mai nel repository):
     - credenziali di accesso al target (chiave SSH, token cloud o kubeconfig)
     - DB_URL, DB_USER, DB_PASSWORD (ruolo applicativo), DB_MIGRATION_USER, DB_MIGRATION_PASSWORD
     - JWT_ISSUER_URI del provider OIDC
     - LICENSE_PUBLIC_KEY
     - AI_API_KEY (se l'assistente è attivo)
  6. Ambienti: staging e production bastano? Serve un ambiente demo pubblico separato?
     Regole di approvazione (required reviewers) sugli environment GitHub.
  7. Migrazioni: eseguite all'avvio dell'API (attuale) o come job separato prima del rollout?
  8. Dominio, certificati TLS, backup, monitoraggio e log centralizzati.
  9. Distribuzione delle app mobile (store) e desktop (firma del codice, aggiornamenti Tauri).
-->

## Decisione

Da prendere.

## Conseguenze

Fino alla decisione il deploy resta manuale e bloccato.
