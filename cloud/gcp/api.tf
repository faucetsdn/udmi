resource "google_project_service" "service" {
  for_each = toset([
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
    "compute.googleapis.com",
    "pubsub.googleapis.com",
    "dns.googleapis.com",
    "iam.googleapis.com",
    "storage.googleapis.com",
    "sourcerepo.googleapis.com",
    "cloudiot.googleapis.com",
    "container.googleapis.com",
    "containerregistry.googleapis.com"
  ])

  service = each.key

  project            = var.gcp_project_id
  disable_on_destroy = false
}