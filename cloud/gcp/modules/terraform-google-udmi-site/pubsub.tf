resource "google_pubsub_topic" "site_pubsub_event_topic" {
  project = var.gcp_project
  name = "${lower(var.site_name)}"
}

resource "google_pubsub_subscription" "site_pubsub_event_subscription" {
  project = var.gcp_project
  name  = "${lower(var.site_name)}-subscription"
  topic = "projects/${var.gcp_project}/topics/${lower(var.site_name)}"

  message_retention_duration = "600s"
  retain_acked_messages      = false

  ack_deadline_seconds = 10

  enable_message_ordering    = false

  expiration_policy {
    ttl = ""
  }

  filter = "attributes.deviceRegistryId = \"${var.site_name}\""

  depends_on = [google_pubsub_topic.site_pubsub_event_topic]

}

resource "google_pubsub_subscription_iam_binding" "site_pubsub_event_subscription_subscriber" {
  project = var.gcp_project
  subscription = "${lower(var.site_name)}-subscription"
  role         = "roles/pubsub.subscriber"
  members = [var.site_group]
  depends_on = [google_pubsub_subscription.site_pubsub_event_subscription]
}