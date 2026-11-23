output "public_ip" {
  description = "IP pubblico (riservato) della macchina: va nel DNS e nel secret OCI_SSH_HOST."
  value       = oci_core_public_ip.app.ip_address
}

output "instance_id" {
  description = "OCID della macchina."
  value       = oci_core_instance.app.id
}

output "shape" {
  description = "CPU e memoria effettive."
  value       = "${oci_core_instance.app.shape} - ${oci_core_instance.app.shape_config[0].ocpus} OCPU, ${oci_core_instance.app.shape_config[0].memory_in_gbs} GB"
}
