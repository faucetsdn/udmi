resource "google_storage_bucket" "function-bucket" {
  for_each           = var.eventHandler_functions
  name               = "${var.gcp_project_name}-${each.value.name}"
  project            = each.value.project
  force_destroy      = true
  storage_class      = each.value.storage_class 
  location           = each.value.location
  versioning {
    enabled = true
  }
}

# Generates an archive of the source code compressed as a .zip file.
data "archive_file" "source" {
  type        = "zip"
  source_dir  = "../../udmif/event-handler/src"
  output_path = "../../udmif/event-handler/dist.zip"
}
# Add the zipped file to the bucket.
resource "google_storage_bucket_object" "function-object" {
  for_each    = var.eventHandler_functions
  name        = "index.zip"
  bucket      = google_storage_bucket.function-bucket[each.key].name
  source      = "data.archive_file.source.output_path"
  lifecycle {
    ignore_changes = [detect_md5hash] 
  }
}
resource "google_cloudfunctions_function" "eventHandlerFunction" {
  for_each              = var.eventHandler_functions
  available_memory_mb   = each.value.available_memory_mb
  entry_point           = each.value.entry_point
  ingress_settings      = "ALLOW_ALL"
  name                  = each.value.name
  project               = each.value.project
  region                = each.value.region
  runtime               = each.value.runtime
   event_trigger  {
      event_type = "providers/cloud.pubsub/eventTypes/topic.publish"
      resource   = "udmi_target"
  } 
  environment_variables = each.value.environment_variables    
  source_archive_bucket = google_storage_bucket.function-bucket[each.key].name
  source_archive_object = google_storage_bucket_object.function-object[each.key].name
}



# IAM Configuration. This allows to provied access to the function.
resource "google_cloudfunctions_function_iam_member" "invoker" {
  for_each       = var.eventHandler_functions 
  project        = google_cloudfunctions_function.eventHandlerFunction[each.key].project
  region         = google_cloudfunctions_function.eventHandlerFunction[each.key].region
  cloud_function = google_cloudfunctions_function.eventHandlerFunction[each.key].name
  role           = "roles/cloudfunctions.invoker"
  member         = var.gcp_access_group
}
