resource "mongodbatlas_project" "udmi" {
  name   = var.project_name
  org_id = var.atlas_org_id
}
## mongodbatlas cluster  
resource "mongodbatlas_cluster" "udmi" {
  project_id             = mongodbatlas_project.udmi.id
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
  provider_auto_scaling_compute_max_instance_size = var.auto_scaling_max_instance_size
  provider_auto_scaling_compute_min_instance_size = var.auto_scaling_min_instance_size
}

# DATABASE USER  
resource "mongodbatlas_database_user" "user" {
  username           = var.db_username
  password           = var.db_password
  project_id         = mongodbatlas_project.udmi.id
  auth_database_name = "admin"

  roles {
    role_name     = var.db_role
    database_name = var.database_name 
  }
}