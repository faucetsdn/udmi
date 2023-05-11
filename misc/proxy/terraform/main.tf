# mqttproxy cloud infrastucture as code 

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
    }
  }
  backend "gcs" {
    bucket      = "@GCP_PROJECT_ID@-terraform-state-bucket"
    prefix      = "proxy"
    credentials = "./auth/credentials.json"
  }
}
