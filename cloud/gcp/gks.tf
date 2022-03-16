variable "gke_num_nodes" {
    type = number 
    default = "1"
    description = "number of gke nodes"
}

#GKE CLUSTER
resource "google_container_cluster" "biot" {
    name     = "${var.gcp_project_id}-gke"
    location = var.gcp_region
    node_locations = ["us-central1-b"]

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  
  remove_default_node_pool = true
  initial_node_count       = 1
  
  network    = google_compute_network.vpc.name
  subnetwork = google_compute_subnetwork.subnet.name
}

# Separately Managed Node Pool
resource "google_container_node_pool" "node_pool" {
  name       = "${var.gcp_project_id}-node-pool"
  location   = var.gcp_region
  cluster    = google_container_cluster.biot.name
  node_count = var.gke_num_nodes

  node_config {
    oauth_scopes = [
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
    ]

    labels = {
      env = var.gcp_project_id
    }

    # preemptible  = true
    machine_type = "n1-standard-1"
    tags         = ["gke-node", "${var.gcp_project_id}-gke"]
    metadata = {
      disable-legacy-endpoints = "true"
    }
  }
}
