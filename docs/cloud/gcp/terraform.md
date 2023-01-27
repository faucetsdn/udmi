[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [Terraform](#)

# UDMI terraform configuration files

The files in the [`cloud`](../../cloud) folder enable the creation of Cloud infrastructure to support the UDMI device-to-cloud data pipeline.

At the moment only Google Cloud Platform (GCP) is supported.

## Pre-requisites

There are some pre-requisites that need to be satisfied in order to use this terraform configuration to setup UDMI infrastructure in a GCP cloud project:

1. A working installation of [terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli?in=terraform/gcp-get-started)
2. An [existing project on GCP](https://cloud.google.com/resource-manager/docs/creating-managing-projects)
4. [Create a service account](https://cloud.google.com/iam/docs/creating-managing-service-accounts) for terraform with Owner role
5. Create a Standard type storage bucket setup with a unique name. example: `udmi-terraform-state-bucket`. You should disable public access and ensure your service account has access to it.
6. [Generate a JSON key](https://cloud.google.com/iam/docs/creating-managing-service-account-keys) for the terraform service account, download it and store it in the [`udmi/cloud/auth`](../../../cloud/gcp/auth) folder with the name `credentials.json`\
7. [Create a UDMI group for your organisation](https://cloud.google.com/iam/docs/groups-in-cloud-console). This group name should be provided in various locations. 
8. [Enable the following APIS manually](https://cloud.google.com/endpoints/docs/openapi/enable-api). Cloud Resource Manager API and Service Usage API.

NOTE: There is currently a limitation in the terraform code that requires the project name and id to be the same.
TODO: create a gcloud based shell script to automatically does all activities listed above.
TODO: add cloud functions generation via terraform configuration.

## Getting started

To get started, copy the `terraform.tfvars.template` file to `terraform.tfvars`, the `udmi-site.tf.template` file to `udmi-site.tf` and the `main.tf.template` file to `main.tf`:

```
cp terraform.tfvars.template terraform.tfvars
cp udmi-site.tf.template udmi-site.tf
cp main.tf.template main.tf
```

Edit them according to the project settings and according to the UDMI site you need to create and group permissions you want to attribute to it.

The next steps are to initialize the required terraform backend, provider and modules and 
to import the project and the bucket previously created to the terraform state: (Note, you only need to do the imports once, unless you remove their associated states from beeing tracked.

```
terraform init
terraform import google_project.udmi-project ${GCP_PROJECT_NAME}
terraform import google_storage_bucket.tf-bucket ${UDMI_TERRAFORM_BUCKET_NAME}
```

where ${GCP_PROJECT_NAME} is the name of the GCP project created in the pre-requisite step 2 and ${UDMI_TERRAFORM_BUCKET_NAME} is the name of the terraform state bucket created in step 3.

The next step is to check that the planned tasks are correct:

```
terraform plan
```

The plan will show all the resources to be created. To execute the plan use the apply command:

```
terraform apply
```

### Troubleshooting

- If you need to reset your infrastructure by using *terraform destroy* (use with caution), you should first remove the tf_bucket and project resources from being tracked in the terraform state:
    ```
    terraform state rm google_project.udmi-project
    terraform state rm google_storage_bucket.tf-bucket
    ```
- If you run into API enablement issues or other issues, you can retry the *plan* and *apply* terraform commands. If you still have issues, you can look at api.tf for a list of APIs that must be enabled and enable them manually.
