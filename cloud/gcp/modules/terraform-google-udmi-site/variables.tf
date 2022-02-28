# define GCP project name
variable "gcp_project" {
  type        = string
  description = "GCP project name"
}

# define GCP project region
variable "gcp_region" {
  type = string
  description = "GCP project region"
}

# define site name
variable "site_name" {
  type        = string
  description = "UDMI site name"
}

# define site region
variable "site_region" {
  type = string
  description = "UDMI site resources region"
}

variable "site_group" {
  type = string
  description = "Group for the UDMI site, for Pub/Sub topic and subscription"
}

variable "site_members" {
  description = "Members of the UDMI site, for Pub/Sub topic and subscription"
  default = []
}

# define log level
variable "log_level" {
  type = string
  description = "Log level"
}
