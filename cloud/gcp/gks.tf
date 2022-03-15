variable "gke_num_nodes" {
    type = number 
    default = "1"
    description = "number of gke nodes"
}

#GKE CLUSTER
resource "google_container_cluster" "biot" {
    name     = "${var.gcp_project_id}-gke"
    location = var.gcp_region

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  
  remove_default_node_pool = true
  initial_node_count       = 1
}

# Separately Managed Node Pool
resource "google_container_node_pool" "node_pool" {
  name       = "${var.gcp_project_id}-node-pool"
  location   = var.gcp_region
  cluster    = google_container_cluster.biot.name
  node_count = var.gke_num_nodes

  node_config {
    preemptible  = true
    machine_type = "e2-medium"
  }
}
