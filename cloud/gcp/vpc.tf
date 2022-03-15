variable "gcp_vpc_name" {
    type = string
    description = "vpc name"
}

# VPC
resource "google_compute_network" "vpc" {
  name                    = "${var.gcp_vpc_name}-vpc"
  auto_create_subnetworks = "false"
}

# Subnet
resource "google_compute_subnetwork" "subnet" {
  name          = "${var.gcp_vpc_name}-subnet"
  region        = var.gcp_region
  network       = google_compute_network.vpc.name
  ip_cidr_range = "10.10.0.0/24"
}