# Cloud Setup

All the tools working with Google Cloud Platform (GCP) projects use GCP's
[application default credentials](https://cloud.google.com/sdk/gcloud/reference/auth/application-default)
model for authenticating interaction with the cloud. Depending on your setup,
this can be used with end-user credentials (`gcloud auth login`) or with a
service account (`gcloud auth activate-service-account`).

1. Identify a GCP project that you can to host the system, or acquire a new one if necessary.
   * Ensure billing has been enabled https://cloud.google.com/billing/docs/how-to/modify-project

2. Identify a GCP IoT Core registry you would like to use, or create a new one
   * Refer to https://cloud.google.com/iot/docs/how-tos/getting-started for additional information
   * Search for IoT Core
   * Click `Enable`