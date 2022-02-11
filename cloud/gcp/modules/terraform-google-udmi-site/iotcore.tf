resource "google_cloudiot_registry" "site_cloudiot_registry" {
    name = var.site_name
    region = var.site_region
    project = var.gcp_project
    event_notification_configs {
        pubsub_topic_name = "projects/${var.gcp_project}/topics/udmi_target"
        subfolder_matches = ""
    }
    state_notification_config = {
         pubsub_topic_name = "projects/${var.gcp_project}/topics/udmi_state"
    } 
    mqtt_config = {
        mqtt_enabled_state = "MQTT_ENABLED"
    }
    http_config = {
        http_enabled_state = "HTTP_DISABLED"
    }
    log_level = var.log_level
}

resource "google_project_iam_member" "bos-corpops-testing_cloudiot_viewer" {
  project = var.gcp_project
  role    = "roles/cloudiot.viewer"
  member = var.site_group
}

resource "google_project_iam_member" "bos-corpops-testing_cloudiot_provisioner" {
  project = var.gcp_project
  role    = "roles/cloudiot.provisioner"
  member = var.site_group
}
