resource "google_service_account" "gke" {
  account_id   = "gkeuser"
  display_name = "GKE Service Account"
  project      = var.gcp_project_id
}

resource "google_project_iam_member" "gke_gcr_binding" {
  project = var.gcp_project_id
  role    = "roles/storage.objectViewer"
  member  = "serviceAccount:${google_service_account.gke.email}"
}

resource "google_project_iam_member" "gke_log_writer" {
  project = var.gcp_project_id
  role    = "roles/logging.admin"
  member  = "serviceAccount:${google_service_account.gke.email}"
}

resource "google_project_iam_member" "gke_monitoring" {
  project = var.gcp_project_id
  role    = "roles/monitoring.admin"
  member  = "serviceAccount:${google_service_account.gke.email}"
}

resource "google_container_cluster" "mqttproxy" {
  project        = var.gcp_project_id
  name           = var.gke_cluster_name
  location       = var.gke_cluster_location
  node_locations = var.gke_node_locations

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.

  remove_default_node_pool = true
  initial_node_count       = var.gke_initial_node_count
  network                  = var.create_vpc ? google_compute_network.vpc[0].name : null
  subnetwork               = var.create_vpc ? google_compute_subnetwork.subnet[0].name : null


  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }
}

# Separately Managed Node Pool
resource "google_container_node_pool" "node_pool" {
  project    = var.gcp_project_id
  name       = var.gke_node_pool_name
  location   = var.gke_cluster_location
  cluster    = google_container_cluster.mqttproxy.name
  node_count = var.gke_num_nodes

  node_config {
    # preemptible  = true
    machine_type = var.gke_machine_type

    # Google recommends custom service accounts that have cloud-platform scope and permissions granted via IAM Roles.
    service_account = google_service_account.gke.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring"
    ]
    metadata = {
      disable-legacy-endpoints = "true"
    }

    workload_metadata_config {
    mode = "GKE_METADATA"
  }

  }
}

#This resource creates static IP
resource "google_compute_global_address" "mqttproxy_global_address" {
  name    = "mqttproxy-global-address"
  project = var.gcp_project_id
}

resource "google_dns_managed_zone" "prod" {
  name     = var.gcp_project_name
  dns_name = var.dns_name
  project  = var.gcp_project_id
}

#This resouce creates ssl certs
resource "google_compute_managed_ssl_certificate" "mqttproxy_ssl_certs" {
  name    = "mqttproxy-ssl"
  project = var.gcp_project_id
  managed {
    domains = var.ssl_domains
  }
}

resource "google_service_account" "haproxy" {
  account_id   = "haproxy"
  display_name = "haproxy service account via workload identity"
  project      = var.gcp_project_id
}

resource "google_project_iam_member" "haproxy_binding" {
  project = var.gcp_project_id
  role    = "roles/editor"
  member  = "serviceAccount:${google_service_account.haproxy.email}"
}

resource "google_service_account_iam_binding" "haproxy_ksa_gsa" {
  service_account_id = google_service_account.haproxy.name
  role               = "roles/iam.workloadIdentityUser"

  members = [
    "serviceAccount:@GCP_PROJECT_ID@.svc.id.goog[default/ksa-haproxy]",
  ]
}

# Create a GCS Bucket
resource "google_storage_bucket" "haproxy_gcs_store" {
  project       = var.gcp_project_id
  name          = "@GCP_PROJECT_ID@-gcs"
  location      = "us-central1"

  versioning {
    enabled = true
  }
  lifecycle {
    # Stop any terraform plan which would destroy this bucket.
    prevent_destroy = true
  }
}
