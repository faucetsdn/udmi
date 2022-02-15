# terraform-google-udmi-site

This terraform module provisions all the resources needed for a UDMI site.

These include:

* IAM group for consistent resources access
* IoT Core Registry for devices and gateways
* Pub/Sub topics and subscriptions for data ingestion
* Source repository for storing configuration files in a revision control system

## Usage

Add one or more Terraform configuration files for the UDMI sites with the following syntax:

```hcl
module "ZZ-TRI-FECTA" {
  source      = "./modules/terraform-google-udmi-site"
  gcp_project = var.gcp_project_name
  site_name   = "ZZ-TRI-FECTA"
  site_region = "europe-west1"
  site_group  = "group:group@example.com"
  log_level   = "DEBUG"
}
```

where in the example `ZZ-TRI-FECTA` is the name of the UDMI site, `europe-west1` is the desired region, `DEBUG` is the log_level.

Substitute these values with the desired ones.