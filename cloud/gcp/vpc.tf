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
