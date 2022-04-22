# Create a GCS Bucket
resource "google_storage_bucket" "tf-bucket" {
  project       = var.gcp_project_id
  name          = var.tf-state-bucket-name
  force_destroy = true
  storage_class = var.tf-state-storage-class
  versioning {
    enabled = true
  }
  lifecycle {
    # Stop any terraform plan which would destroy this GCP project.
    prevent_destroy = true
  }
}
