resource "google_storage_bucket" "function-bucket" {
  name               = "${var.gcp_project_name}-${var.bucket_name}"
  project            = var.gcp_project_id
  force_destroy      = true
  storage_class      = var.storage_class 
  location           = var.bucket_location
  versioning {
    enabled = true
  }
}

# Generates an archive of the source code compressed as a .zip file.
data "archive_file" "source" {
  type        = "zip"
  source_dir  = "../../udmif/event-handler"
  output_path = "../../udmif/event-handler/index.zip"
}
# Add the zipped file to the bucket.
resource "google_storage_bucket_object" "function-object" {
  name        = "index.zip"
  bucket      = google_storage_bucket.function-bucket.name
  source      = data.archive_file.source.output_path
  lifecycle {
    ignore_changes = [detect_md5hash] 
  }
}
resource "google_cloudfunctions_function" "enventHandlerFunction" {
  #count = length(var.eventHandler_functions)
  available_memory_mb   = var.function_memory
  entry_point           = var.function_entry_point
  ingress_settings      = "ALLOW_ALL"
  name                  = var.function_name
  project               = var.gcp_project_id
  region                = var.gcp_region
  runtime               = var.function_runtime
   event_trigger  {
      event_type = "providers/cloud.pubsub/eventTypes/topic.publish"
      resource   = "udmi_target"
  } 
  environment_variables = var.function_environment_variables     
  source_archive_bucket = google_storage_bucket.function-bucket.name
  source_archive_object = google_storage_bucket_object.function-object.name
}



# IAM Configuration. This allows to provied access to the function.
resource "google_cloudfunctions_function_iam_member" "invoker" {
  project        = google_cloudfunctions_function.enventHandlerFunction.project
  region         = google_cloudfunctions_function.enventHandlerFunction.region
  cloud_function = google_cloudfunctions_function.enventHandlerFunction.name
  role           = "roles/cloudfunctions.invoker"
  member         = var.gcp_access_group
}
