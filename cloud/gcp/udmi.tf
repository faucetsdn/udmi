resource "google_cloudiot_registry" "udms_reflect_cloudiot_registry" {
  name    = "UDMS-REFLECT"
  region  = var.gcp_region
  project = var.gcp_project_id
  event_notification_configs {
    pubsub_topic_name = "projects/${var.gcp_project_id}/topics/udmi_reflect"
    subfolder_matches = ""
  }
  mqtt_config = {
    mqtt_enabled_state = "MQTT_ENABLED"
  }
  http_config = {
    http_enabled_state = "HTTP_DISABLED"
  }
  log_level  = var.log_level
  depends_on = [google_pubsub_topic.udms_reflect_pubsub_event_topic]
}

resource "google_project_iam_member" "udmi_cloudiot_viewer" {
  project = var.gcp_project_id
  role    = "roles/cloudiot.viewer"
  member  = var.udmi_access_group
}

resource "google_project_iam_member" "udmi_cloudiot_provisioner" {
  project = var.gcp_project_id
  role    = "roles/cloudiot.provisioner"
  member  = var.udmi_access_group
}

resource "google_pubsub_topic" "udms_target_pubsub_event_topic" {
  project = var.gcp_project_id
  name    = "udmi_target"
}

resource "google_pubsub_topic" "udms_state_pubsub_event_topic" {
  project = var.gcp_project_id
  name    = "udmi_state"
}

resource "google_pubsub_topic" "udms_reflect_pubsub_event_topic" {
  project = var.gcp_project_id
  name    = "udmi_reflect"
}

resource "google_pubsub_topic" "udms_config_pubsub_event_topic" {
  project = var.gcp_project_id
  name    = "udmi_config"
}

resource "google_pubsub_subscription" "udms_reflect_pubsub_event_subscription" {
  project = var.gcp_project_id
  name    = "udmi_reflect-subscription"
  topic   = "projects/${var.gcp_project_id}/topics/udmi_reflect"

  # 10 minutes
  message_retention_duration = "600s"
  retain_acked_messages      = true

  ack_deadline_seconds = 10

  enable_message_ordering = false

  expiration_policy {
    ttl = ""
  }

  depends_on = [google_pubsub_topic.udms_reflect_pubsub_event_topic]

}

resource "google_pubsub_subscription_iam_binding" "udms_reflect_pubsub_event_subscription_subscriber" {
  project      = var.gcp_project_id
  subscription = "udmi_reflect-subscription"
  role         = "roles/pubsub.subscriber"
  members      = ["${var.udmi_access_group}"]
  depends_on   = [google_pubsub_subscription.udms_reflect_pubsub_event_subscription]
}