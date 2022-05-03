terraform {
  required_version = ">= 0.12"
}
provider "google" {
  credentials = file(var.gcp_auth_file)
  project     = var.gcp_project_name
  region      = var.gcp_region
}

provider "mongodbatlas" {
  public_key  = var.public_key
  private_key = var.private_key
}

