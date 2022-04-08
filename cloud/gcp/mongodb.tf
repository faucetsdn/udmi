resource "mongodbatlas_cluster" "udmi" {
  project_id               = var.gcp_project_id
  name                   = "nonprod"
  mongo_db_major_version = "5.0"
  cluster_type           = "REPLICASET"
  replication_specs {
    num_shards = 1
    regions_config {
      region_name     = "us-central1"
      electable_nodes = 3
      priority        = 7
      read_only_nodes = 0
    }
  }
  # Provider Settings "block"
  provider_name                = "GCP"
  disk_size_gb                 = 20
  provider_instance_size_name  = "M10"
  auto_scaling_disk_gb_enabled = true
  provider_auto_scaling_compute_max_instance_size = "M30"
}