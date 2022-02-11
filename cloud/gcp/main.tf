# UDMI cloud infrastucture as code 

resource "google_project" "udmi-project" {
  project_id = var.gcp_project_id
  name       = var.gcp_project_name

  lifecycle {
    # Stop any terraform plan which would destroy this GCP project.
    prevent_destroy = true
  }
}

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 3.0"
    }
  }
  backend "gcs" {
    bucket      = "udmi-terraform-state-bucket"
    prefix      = "udmi"
    credentials = "./auth/credentials.json"
  }
}







