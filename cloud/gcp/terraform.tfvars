# GCP Settings
gcp_project_id   = "udmi-prod"
gcp_project_name = "udmi-prod"
gcp_region       = "us-central1"
gcp_auth_file    = "./auth/credentials.json"

# Storage Buckets
tf-state-bucket-name   = "udmi-prod-terraform-state"
tf-state-storage-class = "STANDARD"

# Access
gcp_access_group = "group:udmi@buildingsiot.com"

# Log level
log_level = "DEBUG"

#GKE and other settings
#cloud DNS variables and ssl domains for UDMIF. Use a domain you own.
dns_name    = "udmi.buildingsiot.com."
ssl_domains = ["web.udmi.buildingsiot.com", "api.udmi.buildingsiot.com"]

#MongoDB Atlas
atlas_org_id = "5d0a7dff79358e0ade2c640c"
public_key   = "huauexqt"
private_key  = "1aa9bd7c-f767-496e-9c70-308484de8a4d"
db_password  = "C0mPlxPw4"

#gke variables
gke_cluster_name       = "udmi-staging-gke"
gke_num_nodes          = 1
gke_initial_node_count = 1
gke_node_pool_name     = "udmi-staging-node-pool"
gke_cluster_location   = "us-central1-b"
gke_machine_type       = "n1-standard-1"


