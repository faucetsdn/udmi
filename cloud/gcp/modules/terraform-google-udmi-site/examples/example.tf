module "UK-LON-TEST" {
  source      = "./modules/terraform-google-bos-site"
  gcp_project = var.gcp_project
  site_name   = "UK-LON-TEST"
  site_region = "europe-west1"
  site_group  = "group:bos-corpops-testing-users-group@google.com"
  log_level   = "DEBUG"
  tags = {
    Environment = "dev"
  }
}