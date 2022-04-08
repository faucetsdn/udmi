resource "mongodbatlas_cluster" "udmi" {
  project_id             = var.gcp_project_id
  name                   = var.cluster_name
  mongo_db_major_version = var.mongodb_version
  cluster_type           = "REPLICASET"
  replication_specs {
    num_shards = 1
    regions_config {
      region_name     = var.cluster_region
      electable_nodes = 3
      priority        = 7
      read_only_nodes = 0
    }
  }
  # Provider Settings "block"
  provider_name                = var.provider_name
  disk_size_gb                 = var.disk_size_gb
  provider_instance_size_name  = var.instance_size_name
  auto_scaling_disk_gb_enabled = true
  provider_auto_scaling_compute_max_instance_size = var.auto_scaling_instance_size
}