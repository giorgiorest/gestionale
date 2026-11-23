# Infrastruttura di produzione su Oracle Cloud (Always Free): rete, porte, macchina, IP fisso.
# La eseguono i workflow "Infrastruttura": vedi README.md in questa cartella.

terraform {
  required_version = ">= 1.12"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 9.14"
    }
  }

  # Stato su Oracle Object Storage. Bucket, namespace e credenziali arrivano da
  # -backend-config (li genera il workflow): nessun valore dell'account nel repository.
  backend "oci" {}
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}
