[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [Terraform](#)

# UDMI terraform configuration files

The files in the [`cloud`](../../cloud) folder enable the creation of Cloud infrastructure to support the UDMI device-to-cloud data pipeline.

At the moment only Google Cloud Platform (GCP) is supported.

## Pre-requisites

There are some pre-requisites that need to be satisfied in order to use this terraform configuration to setup UDMI infrastructure in a GCP cloud project:

1. A working installation of [terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli?in=terraform/gcp-get-started)
2. An [existing project on GCP](https://cloud.google.com/resource-manager/docs/creating-managing-projects)
3. A [standard type storage bucket](https://cloud.google.com/storage/docs/creating-buckets) for uploading terraform blobs
4. A [service account](https://cloud.google.com/iam/docs/creating-managing-service-accounts) for terraform with Owner role
5. A [JSON key](https://cloud.google.com/iam/docs/creating-managing-service-account-keys) for the terraform service account,
   downloaded to the `udmi/cloud/gcp/auth` folder with the name `credentials.json`

TODO: create a gcloud based shell script to automatically does all activities listed above.

TODO: add cloud functions generation via terraform configuration.

## Getting started

To get started, go to the `cloud/gcp` directory and copy the `terraform.tfvars.template` file to `terraform.tfvars`
and the `udmi-site.tf.template` file to `udmi-site.tf`:

```
cd cloud/gcp
cp terraform.tfvars.template terraform.tfvars
cp udmi-site.tf.template udmi-site.tf
```

Edit them according to the project settings and according to the UDMI site you need to create and group permissions you want to attribute to it.
* TODO: Clarify what "group permissions" are?  No idea what to put there.
* TODO: Clarify what to change the log level to?
* TODO: Seems easier to just need to change the setting to point at the service account key, rather than moving the key to a specific place.

Set ${GCP_PROJECT_NAME} to name of the GCP project created in the pre-requisite step 2.

Then initialize the required terraform backend, provider and modules and 
to import the project and the bucket previously created to the terraform state:

```
terraform init
terraform import google_project.udmi-project ${GCP_PROJECT_NAME}
terraform import google_storage_bucket.tf-bucket udmi-terraform-state-bucket
```

The next step is to check that the planned tasks are correct:

```
terraform plan
```

The plan will show all the resources to be created. To execute the plan use the apply command:

```
terraform apply
```

