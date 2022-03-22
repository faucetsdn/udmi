variable "gke_num_nodes" {
    type = number 
    description = "number of gke nodes"
}
variable "node_locations" {
  type = list(string)
  description = "The list of zones in which the cluster's nodes are located"
}

variable "initial_node_count" {
  type = number
  default = 1
  description = "The number of nodes to create in this cluster"
}

variable "gke_cluster_name" {
  type = string
  description = "gke cluster name"
}

variable "node_pool_name" {
  type = string
  description = "The name of the node pool"
}
variable "gke_cluster_location" {
  type = string
  description = "The location (region or zone) of the cluster"
}
variable "machine_type" {
  type = string
  description = "Type of machine"
}

#GKE CLUSTER
resource "google_container_cluster" "udmi" {
    name     = var.gke_cluster_name
    location = var.gke_cluster_location
    node_locations = var.node_locations

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  
  remove_default_node_pool = true
  initial_node_count       = var.initial_node_count
  
  network    = var.create_vpc ? google_compute_network.vpc[0].name : null
  subnetwork = var.create_vpc ? google_compute_subnetwork.subnet[0].name : null
}

# Separately Managed Node Pool
resource "google_container_node_pool" "node_pool" {
  name       = var.node_pool_name
  location   = var.gke_cluster_location
  cluster    = google_container_cluster.udmi.name
  node_count = var.gke_num_nodes

  node_config {

    # preemptible  = true
    machine_type = var.machine_type
    metadata = {
      disable-legacy-endpoints = "true"
    }
  }
}
