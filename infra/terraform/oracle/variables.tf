# --- Accesso all'account (dai GitHub Secrets dell'environment "oracle-cloud") --------------------

variable "tenancy_ocid" {
  description = "OCID della tenancy (account Oracle Cloud)."
  type        = string
}

variable "user_ocid" {
  description = "OCID dell'utente a cui appartiene la chiave API."
  type        = string
}

variable "fingerprint" {
  description = "Impronta della chiave API."
  type        = string
}

variable "private_key_path" {
  description = "Percorso della chiave privata API (scritta dal workflow in un file temporaneo)."
  type        = string
}

variable "region" {
  description = "Regione di casa dell'account, es. eu-turin-1."
  type        = string
}

variable "compartment_ocid" {
  description = "Compartment in cui creare le risorse. Vuoto = compartment principale (root)."
  type        = string
  default     = ""
}

# --- Macchina ---------------------------------------------------------------------------------------

variable "name" {
  description = "Prefisso dei nomi delle risorse."
  type        = string
  default     = "civilbeefy"
}

variable "shape" {
  description = "Tipo di macchina. VM.Standard.A1.Flex (Ampere, arm64) rientra nell'Always Free."
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "ocpus" {
  description = "CPU (OCPU). Always Free: fino a 4 in totale tra le macchine A1."
  type        = number
  default     = 2
}

variable "memory_gb" {
  description = "Memoria in GB. Always Free: fino a 24 in totale tra le macchine A1."
  type        = number
  default     = 12
}

variable "boot_volume_gb" {
  description = "Disco di avvio in GB. Always Free: 200 GB in totale tra tutti i dischi."
  type        = number
  default     = 100

  validation {
    condition     = var.boot_volume_gb >= 50 && var.boot_volume_gb <= 200
    error_message = "Il disco deve essere tra 50 e 200 GB (limite Always Free)."
  }
}

variable "availability_domain" {
  description = "Availability domain. Vuoto = il primo della regione."
  type        = string
  default     = ""
}

variable "ssh_public_keys" {
  description = "Chiavi SSH pubbliche autorizzate per l'utente ubuntu, una per riga (la tua e quella del deploy)."
  type        = string

  validation {
    condition     = length(trimspace(var.ssh_public_keys)) > 0
    error_message = "Serve almeno una chiave SSH pubblica (variabile OCI_SSH_PUBLIC_KEYS)."
  }
}

variable "app_dir" {
  description = "Cartella dell'applicazione sulla macchina (deve coincidere con OCI_APP_DIR del Deploy)."
  type        = string
  default     = "/opt/civilbeefy"
}

# --- Rete ---------------------------------------------------------------------------------------------

variable "vcn_cidr" {
  description = "Indirizzi della rete virtuale."
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "Indirizzi della subnet pubblica."
  type        = string
  default     = "10.0.0.0/24"
}

variable "ssh_allowed_cidr" {
  description = "Da dove è ammesso SSH. 0.0.0.0/0 serve al workflow Deploy (gli indirizzi di GitHub cambiano)."
  type        = string
  default     = "0.0.0.0/0"
}
