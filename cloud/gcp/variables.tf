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

variable "tf-state-bucket-name" {
  type        = string
  description = "The name of the Google Storage Bucket to store the Terraform state"
}

variable "tf-state-storage-class" {
  type        = string
  description = "The storage class of the Storage Bucket to store the Terraform state"
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

## GKE variables ##
variable "gke_num_nodes" {
  type        = number
  default     = 1
  description = "number of gke nodes"
}
variable "gke_node_locations" {
  type        = list(string)
  default     = []
  description = "The list of zones in which the cluster's nodes are located"
}

variable "gke_initial_node_count" {
  type        = number
  default     = 1
  description = "The number of nodes to create in this cluster's default node pool"
}

variable "gke_cluster_name" {
  type        = string
  default     = "udmi"
  description = "Cluster name"
}

variable "gke_node_pool_name" {
  type        = string
  default     = "udmi-pool"
  description = "The name of the node pool"
}
variable "gke_cluster_location" {
  type        = string
  default     = "us-central1-f"
  description = "The location (region or zone) of the cluster"
}
variable "gke_machine_type" {
  type        = string
  default     = "e2-standard-2"
  description = "The name of a Google Compute Engine machine type"
}
#cloud DNS variables
variable "dns_name" {
  type        = string
  description = "The DNS name of the udmi managed zone"
}
#ssl variable
variable "ssl_domains" {
  type        = list(string)
  description = "Domains for which a managed SSL certificate will be valid"
}
##vpc variables##
variable "gcp_vpc_name" {
  type        = string
  default     = "udmi"
  description = "Name of the VPC that will be created when create_vpc=true"
}
variable "ip_cidr_range" {
  type        = string
  default     = "10.10.0.0/24"
  description = "CIDR range of the VPC when creating a new one"
}
variable "create_vpc" {
  type        = bool
  default     = false
  description = "Indicates if we use the default vpc or create a new one."
}

## Postgresql variables
variable "instance_name" {
  type = string
  description = "The name of the DB instance"
}
variable "database_version" {
  type = string
  description = "DB Server version to use"
}
variable "region" {
  type = string
  description = "The region the instance will sit in."
}
variable "tier" {
  type = string
  description = "custom machine type to use"
}
variable "deletion_protection" {
  type = bool
  default = true
  description = "Whether or not to allow Terraform to destroy the instance"
}
variable "environment" {
  type = string
  description = "user label of the instace"
}
variable "db_name" {
  type = string
  description = "database name in GCP"
}
variable "db_user" {
  type = string
  description = "Postgresql db user name in GCP"
}
variable "password" {
  type = string
  description = "Postgresql db password in GCP"
}

