resource "google_sql_database_instance" "main" {
  name             = var.instance_name 
  database_version = var.database_version 
  region           = var.region
  deletion_protection = var.deletion_protection
  
  settings {
    # Second-generation instance tiers are based on the machine
    # type. See argument reference below.
    tier = var.tier 
    user_labels = {
      "environment" = var.environment
    }
    backup_configuration {
      location = "us"
      point_in_time_recovery_enabled = true
      enabled = true
      start_time = "00:00"
    }
    maintenance_window {
      day = 7
      hour = 0
      update_track = "stable"
    }
  }
}

resource "google_compute_network" "default" {
  name = "default"
  description = "Default network for the project"
}

resource "google_compute_global_address" "default_ip_address" {
  name          = "default-ip-address"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.default.id
}

resource "google_service_networking_connection" "default_vpc_connection" {
  network                 = google_compute_network.default.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.default_ip_address.name]
}

resource "google_sql_database" "database" {
    name = var.db_name
    instance = google_sql_database_instance.main.name
} 

resource "google_sql_user" "users" {
  name     = var.db_user 
  instance = google_sql_database_instance.main.name
  password = var.password  
}