output "event_topic" {
    value = google_pubsub_subscription.site_pubsub_event_subscription.name
}

output "site_members" {
    value = var.site_members
}
