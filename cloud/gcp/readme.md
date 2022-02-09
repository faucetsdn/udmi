# UDMI terraform configuration files

The files in this folder enable the creation of Cloud infrastructure to support the UDMI device-to-cloud data pipeline.

At the moment only Google Cloud Platform (GCP) is supported.

## Pre-requisites

There are some pre-requisites that need to be satisfied in order to use this terraform configuration to setup UDMI infrastructure in a GCP cloud project:

1. A working installation of [terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli?in=terraform/gcp-get-started)
2. An [existing project on GCP](https://cloud.google.com/resource-manager/docs/creating-managing-projects)
3. A Standard type storage bucket setup with the name `udmi-terraform-state-bucket`
4. [Create a service account](https://cloud.google.com/iam/docs/creating-managing-service-accounts) for terraform with Owner role
5. [Generate a JSON key](https://cloud.google.com/iam/docs/creating-managing-service-account-keys) for the terraform service account, download it and store it in the [`udmi/cloud/auth`](./auth) folder with the name `credentials.json`

TODO: create a gcloud based shell script to automatically does all activities listed above.

## Getting started

To get started, copy the `terraform.tfvars.template` file to `terraform.tfvars`

```
cp terraform.tfvars.template terraform.tfvars
```

and edit it according to the project settings.

Also copy the `udmi-site.tf.template` file to `udmi-site.tf` 

```
cp udmi-site.tf.template udmi-site.tf
```

and edit it according to the UDMI site you need to create and group permissions you want to attribute to it.

The next step is to initialize the required terraform backend, provider and modules:

```
terraform init
```

It is then necessary to import the project and the bucket previously created to the terraform state:

```
terraform import google_project.udmi-project GCP-PROJECT-NAME
terraform import google_storage_bucket.tf-bucket udmi-terraform-state-bucket
```

where GCP-PROJECT-NAME is the name of the GCP project created in the pre-requisite step 2.

The next step is to check that the planned tasks are correct:

```
terraform plan
```

The plan will show all the resources to be created. To execute the plan use the apply command:

```
terraform apply
```

