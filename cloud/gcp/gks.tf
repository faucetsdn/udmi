resource "google_service_account" "gke" {
  account_id   = "gkeuser"
  display_name = "GKE Service Account"
  project = var.gcp_project_id
}
resource "google_project_iam_member" "gke_gcr_binding" {
  project = var.gcp_project_id
  role    = "roles/storage.objectViewer"
  member  = "serviceAccount:${google_service_account.gke.email}"
}
#GKE CLUSTER
resource "google_container_cluster" "udmi" {
    name     = var.gke_cluster_name
    location = var.gke_cluster_location
    node_locations = var.gke_node_locations

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.

  remove_default_node_pool = true

  initial_node_count       = var.gke_initial_node_count
  
  network    = var.create_vpc ? google_compute_network.vpc[0].name : null
  subnetwork = var.create_vpc ? google_compute_subnetwork.subnet[0].name : null
}

# Separately Managed Node Pool
resource "google_container_node_pool" "node_pool" {
  name       = var.gke_node_pool_name
  location   = var.gke_cluster_location
  cluster    = google_container_cluster.udmi.name
  node_count = var.gke_num_nodes

  node_config {

    # preemptible  = true
    machine_type = var.gke_machine_type

    # Google recommends custom service accounts that have cloud-platform scope and permissions granted via IAM Roles.
    service_account = google_service_account.gke.email
    oauth_scopes    = [
      "https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring"
    ]
    metadata = {
      disable-legacy-endpoints = "true"
    }
  }
}

#This resource creates static IP
resource "google_compute_global_address" "udmi_global_address"{
 name = "udmi-global-address"
 project = var.gcp_project_id
}

#This resource creates clouddns entry
resource "google_dns_managed_zone" "udmi_dns_zone" {
  name     = var.gcp_project_name
  dns_name = var.dns_name
  project = var.gcp_project_id
}

#This resource creates A record in cloud dns
resource "google_dns_record_set" "dns_record" {
  project = var.gcp_project_id
  managed_zone = var.gcp_project_name
  name = "*.${var.dns_name}"
  type = "A"
  ttl = 300
  rrdatas = ["${google_compute_global_address.udmi_global_address.address}"]
}

#This resouce creates ssl certs
resource "google_compute_managed_ssl_certificate" "udmi_ssl_certs" {
  name = "udmi-ssl"
  project = var.gcp_project_id
  managed {
    domains = var.ssl_domains
  }
}

