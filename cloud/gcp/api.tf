resource "google_project_service" "site_iam" {
  project = var.gcp_project_id
  service = "iam.googleapis.com"
}

resource "google_project_service" "site_storage" {
  project = var.gcp_project_id
  service = "storage.googleapis.com"
}

resource "google_project_service" "site_sourcerepo" {
  project = var.gcp_project_id
  service = "sourcerepo.googleapis.com"
}