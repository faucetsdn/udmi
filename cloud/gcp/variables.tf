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
variable "function_name" {
    type = string
    default = "transform-EventHandler"
    description = "functions name"
}
variable "function_memory" {
    type = number
    default = 256
    description = "The amount of memory in megabytes allotted for the function to use."
}
variable "function_runtime" {
    type = string 
    default = "nodejs14"
    description = "The runtime in which the function will be executed."
}
variable "function_timeout" {
    type = number
    default = 20
    description = "The amount of time in seconds allotted for the execution of the function."
}
variable "function_entry_point" {
     type = string
     default = "udmi-EventHandler"
     description = "The name of a method in the function source which will be invoked when the function is executed."
}
variable "function_environment_variables" {
  type        = map(string)
  default     = {
    MONGO_PROTOCOL= "mongodb+srv"
    MONGO_USER= ""
    MONGO_PWD= ""
    MONGO_HOST= "preprod.imvgx.mongodb.net/udmi?retryWrites=true&w=majority"
    MONGO_DB= "udmi"
  }
  description = "A set of key/value environment variable pairs to assign to the function."
}