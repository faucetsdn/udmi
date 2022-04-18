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
variable "function_name" {
    type = string
    description = "functions name"
}
variable "storage_class" {
  type =string
  description = "storage class name for function bucket"
}
variable "bucket_location" {
  type = string
  description = "gcp function bucket location"   
}
variable "function_memory" {
    type = number
    description = "The amount of memory in megabytes allotted for the function to use."
}
variable "function_runtime" {
    type = string 
    description = "The runtime in which the function will be executed."
}
variable "function_entry_point" {
     type = string
     description = "The name of a method in the function source which will be invoked when the function is executed."
}
variable "function_environment_variables" {
  type        = map(string)
  description = "A set of key/value environment variable pairs to assign to the function."
}
## Mongodb variables
variable "public_key" {
  type  = string
  description = "mangodb api public key"
}
variable "private_key" {
  type  = string
  description = "mangodb api private key"
}
variable "project_name" {
  type = string
  description = "mangodb project name"
}
variable "atlas_org_id" {
  type = string
  description = "atlas orgination id "
}
variable "cluster_name" {
  type = string 
  description = "Mongodbatlas Cluster Name"
}
variable "mongodb_version" {
  type = string 
  description = "Mongodbatlas version"
}
variable "cluster_region" {
  type = string 
  description = "Mongodbatlas cluster region"
}
variable "provider_name" {
  type = string 
  description = "Mongodbatlas cloud provider"
}
variable "disk_size_gb" {
  type = string 
  description = "mongodb space were we are using for this project"
}
variable "instance_size_name" {
  type = string 
  description = "mongodb space name were we are using for this project"
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
  description = "MongoDB Atlas Database User Name"
}
variable "db_password" {
  type        = string
  description = "MongoDB Atlas Database User Password"
}
variable "database_name" {
  type        = string
  description = "The database in the cluster to limit the database user to, the database does not have to exist yet"
}
variable "db_role" {
  type = string 
  description = "the role where the db user can acess"
}




