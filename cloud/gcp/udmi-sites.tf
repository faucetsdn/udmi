module "prod-biot-test" {
  source      = "./modules/terraform-google-udmi-site"
  gcp_project = var.gcp_project_name
  gcp_region  = var.gcp_region
  site_name   = "prod-biot-test"
  site_region = "us-central1"
  site_group  = var.gcp_access_group
  log_level   = "DEBUG"
}
