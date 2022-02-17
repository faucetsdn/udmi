# define GCP region
variable "gcp_region" {
  type        = string
  description = "GCP region"
}

# define GCP project name
variable "gcp_project_name" {
  type        = string
  description = "GCP project name"
}

# define GCP project id
variable "gcp_project_id" {
  type        = string
  description = "GCP project ID"
}

# GCP authentication file
variable "gcp_auth_file" {
  type        = string
  description = "GCP authentication file"
}

# GCP flaky control
variable "gcp_flakiness_sleep" {
  type        = number
  description = "GCP provisioning sleep to control flakiness"
  default     = 2
}

variable "tf-state-bucket-name" {
  type        = string
  description = "The name of the Google Storage Bucket to create to store the Terraform state"
}

variable "tf-state-storage-class" {
  type        = string
  description = "The storage class of the Storage Bucket to create to store the Terraform state"
}

variable "gcp_access_group" {
  type        = string
  description = "Access group for project-wide cloud infrastructure"
}

# define log level
variable "log_level" {
  type        = string
  description = "Log level"
}