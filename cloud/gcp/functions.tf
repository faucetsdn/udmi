resource "google_storage_bucket" "function-bucket" {
  name     = "${var.gcp_project_id}-${var.function_name}"
  location = var.gcp_region
}

data "archive_file" "source" {
  type = "zip"
  source_dir  = "./src"
  output_path = "./function.zip"
}
resource "google_pubsub_topic" "topic" {
  name    = "udmi_target"
  project = "${var.gcp_project_id}"
}



# Add the zipped file to the bucket.
resource "google_storage_bucket_object" "function-object" {
  # We can avoid unnecessary redeployments by validating the code is unchanged, and forcing
  # a redeployment when it has!
  name   = "index.zip"
  bucket = google_storage_bucket.function-bucket.name
  source = data.archive_file.source.output_path
}
# The cloud function resource.
resource "google_cloudfunctions_function" "functions" {
  available_memory_mb = var.function_memory
  entry_point         = var.function_entry_point
  ingress_settings    = "ALLOW_ALL"

  name                  = var.function_name
  project               = var.gcp_project_name
  region                = var.gcp_region
  runtime               = var.function_runtime 
  timeout               = var.function_timeout
   event_trigger  {
      event_type = "providers/cloud.pubsub/eventTypes/topic.publish"
      resource   = "${google_pubsub_topic.topic.name}"
  } 
  environment_variables = var.function_environment_variables     
  source_archive_bucket = google_storage_bucket.function-bucket.name
  source_archive_object = google_storage_bucket_object.function-object.name
}

# IAM Configuration. This allows unauthenticated, public access to the function.
# Change this if you require more control here.
resource "google_cloudfunctions_function_iam_member" "invoker" {
  project        = google_cloudfunctions_function.functions.project
  region         = google_cloudfunctions_function.functions.region
  cloud_function = google_cloudfunctions_function.functions.name

  role   = "roles/cloudfunctions.invoker"
  member = var.gcp_access_group
}
