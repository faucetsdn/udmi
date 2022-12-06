resource "google_sql_database_instance" "main" {
  name             = var.instance_name 
  database_version = var.database_version 
  region           = var.region 
  
  settings {
    # Second-generation instance tiers are based on the machine
    # type. See argument reference below.
    tier = var.tier 

    maintenance_window {
      update_track = "stable"
    }
  }
}