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
  default     = "mqttproxy"
  description = "Cluster name"
}

variable "gke_node_pool_name" {
  type        = string
  default     = "mqttproxy-pool"
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
  description = "The DNS name of the mqttproxy managed zone"
}
#ssl variable
variable "ssl_domains" {
  type        = list(string)
  description = "Domains for which a managed SSL certificate will be valid"
}
##vpc variables##
variable "gcp_vpc_name" {
  type        = string
  default     = "mqttproxy"
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
