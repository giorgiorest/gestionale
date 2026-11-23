data "oci_identity_availability_domains" "all" {
  compartment_id = var.tenancy_ocid
}

# Ubuntu 24.04 più recente compatibile con la macchina (esclude le versioni "Minimal").
data "oci_core_images" "ubuntu" {
  compartment_id           = local.compartment_id
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = var.shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"

  filter {
    name   = "display_name"
    values = ["^Canonical-Ubuntu-24\\.04-(aarch64-)?[0-9]"]
    regex  = true
  }
}

locals {
  availability_domain = var.availability_domain != "" ? var.availability_domain : data.oci_identity_availability_domains.all.availability_domains[0].name
}

resource "oci_core_instance" "app" {
  compartment_id      = local.compartment_id
  availability_domain = local.availability_domain
  display_name        = var.name
  shape               = var.shape

  shape_config {
    ocpus         = var.ocpus
    memory_in_gbs = var.memory_gb
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = var.boot_volume_gb
    boot_volume_vpus_per_gb = 10 # Balanced: incluso nell'Always Free
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    hostname_label   = var.name
    assign_public_ip = false # l'IP pubblico è quello riservato qui sotto
  }

  metadata = {
    ssh_authorized_keys = trimspace(var.ssh_public_keys)
    # Primo avvio: porte 80/443 nel firewall della macchina, Docker, cartella dell'app.
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml", {
      app_dir = var.app_dir
    }))
  }

  lifecycle {
    # Una nuova immagine Ubuntu o un cloud-init modificato non devono ricreare la macchina:
    # valgono solo per una macchina nuova. Gli aggiornamenti li fa unattended-upgrades.
    ignore_changes = [
      source_details[0].source_id,
      metadata,
      defined_tags,
      create_vnic_details[0].defined_tags,
    ]
  }
}

# IP pubblico riservato: non cambia anche se la macchina viene ricreata (DNS e Deploy restano validi).
data "oci_core_vnic_attachments" "app" {
  compartment_id = local.compartment_id
  instance_id    = oci_core_instance.app.id
}

data "oci_core_private_ips" "app" {
  vnic_id = data.oci_core_vnic_attachments.app.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "app" {
  compartment_id = local.compartment_id
  display_name   = "${var.name}-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.app.private_ips[0].id
}
