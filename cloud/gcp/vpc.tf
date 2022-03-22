variable "gcp_vpc_name" {
    type = string
    default = "udmi"
    description = "vpc name"
}
variable "ip_cidr_range" {
  type = string
  default = "10.10.0.0/24"
  description = "The range of internal addresses that are owned by this subnetwork"
}
variable "create_vpc" {
  type = bool
  default = false
  description = "we can use default vpc or new vpc"
}
# VPC
resource "google_compute_network" "vpc" {
  count                   = var.create_vpc ? 1 : 0
  name                    = "${var.gcp_vpc_name}-vpc"
  auto_create_subnetworks = "false"
}

# Subnet
resource "google_compute_subnetwork" "subnet" {
  count         = var.create_vpc ? 1 : 0
  name          = "${var.gcp_vpc_name}-subnet"
  region        = var.gcp_region
  network       = google_compute_network.vpc[count.index].name
  ip_cidr_range = var.ip_cidr_range
}
