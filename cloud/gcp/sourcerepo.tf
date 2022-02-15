resource "google_sourcerepo_repository" "udmi-terraform" {
  name    = "udmi-terraform"
  project = var.gcp_project_id
}