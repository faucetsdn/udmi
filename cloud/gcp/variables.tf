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

##GKE variables##
variable "gke_num_nodes" {
    type = number
    default = 1
    description = "number of gke nodes"
}
variable "gke_node_locations" {
  type = list(string)
  default = []
  description = "The list of zones in which the cluster's nodes are located"
}

variable "gke_initial_node_count" {
  type = number
  default = 0
  description = "The number of nodes to create in this cluster"
}

variable "gke_cluster_name" {
  type = string
  default = "udmi"
  description = "gke cluster name"
}

variable "gke_node_pool_name" {
  type = string
  default = "udmi"
  description = "The name of the node pool"
}
variable "gke_cluster_location" {
  type = string
  default = "us-central1-f"
  description = "The location (region or zone) of the cluster"
}
variable "gke_machine_type" {
  type = string
  default = "e2-standard-2"
  description = "Type of machine"
}
#cloud DNS variables
variable "dns_name" {
  type = string
  description = "DNS name"
}
#ssl variable
variable "ssl_domains" {
  type = list(string)
  description = "list of domain names"
}
##vpc variables##
variable "gcp_vpc_name" {
    type = string
    default = "udmi"
    description = "vpc name"
}
variable "ip_cidr_range" {
  type = string
  default = "10.10.0.0/24"
  description = "The range of internal addresses that are owned by this subnetwork"
}
variable "create_vpc" {
  type = bool
  default = false
  description = "we can use default vpc or new vpc"
}

## function variables 
variable "eventHandler_functions" {
  type = map(object({
    name                  = string 
    runtime               = string
    available_memory_mb   = number
    entry_point           = string 
    project               = string
    region                = string
    storage_class         = string
    location              = string
    environment_variables = map(string)
  }))
  description = "eventHandler function values"
}
## Mongodb variables
variable "public_key" {
  type  = string
  description = "This is the public key of udmi MongoDB Atlas API key pair."
}
variable "private_key" {
  type  = string
  description = "This is the private key of udmi MongoDB Atlas key pair."
}
variable "project_name" {
  type = string
  description = "The name of the project udmi wants to create"
}
variable "atlas_org_id" {
  type = string
  description = "The ID of the organization udmi want to create the project within."
}
variable "cluster_name" {
  type = string 
  description = "Name of the cluster as it appears in Atlas. Once the cluster is created, its name cannot be changed."
}
variable "mongodb_version" {
  type = string 
  description = "Version of the cluster to deploy."
}
variable "cluster_region" {
  type = string 
  description = "Physical location of your MongoDB cluster."
}
variable "provider_name" {
  type = string 
  description = "Cloud service provider on which the servers are provisioned."
}
variable "disk_size_gb" {
  type = string 
  description = "mongodb space were we are using for udmi project"
}
variable "instance_size_name" {
  type = string 
  description = "mongodb space name were we are using for udmi project"
}
variable "auto_scaling_max_instance_size" {
  type = string 
  description = "auto scalling max instance size when it require"
}
variable "auto_scaling_min_instance_size" {
  type = string 
  description = "auto scalling min instance size when it require"
}
variable "db_username" {
  type        = string
  description = "Username for authenticating to MongoDB."
}
variable "db_password" {
  type        = string
  description = "User's initial password. A value is required to create the database user"
}
variable "database_name" {
  type        = string
  description = "Database on which the user has the specified role."
}
variable "db_role" {
  type = string 
  description = "Name of the role to grant."
}



