# Cloud Setup



1.  Identify an existing GCP to host the system, or acquire a new one if necessary.
    *   Ensure [billing](https://cloud.google.com/billing/docs/how-to/modify-project) 
        has been enabled 
2.  Identify a GCP IoT Core registry you would like to use, or create a new one if necesary
    *   Refer to [GCP IoT Core Getting Started](https://cloud.google.com/iot/docs/how-tos/getting-started)
        for additional information
    *   Search for IoT Core in the GCP Console
    *   Click `Enable`
    *   Click to create a new Registry
3.  Assign PUB/SUB topics to the registry as described in the [Message Walk Guidance](message_walk.md). 
    If the topics do not exist, create them
    *   Set the default _Cloud Pub/Sub topic_ for the registry to the topic `udmi_target` 
    *   Add an additional _Cloud Pub/Sub topic_ `udmi_config` to the subfolder `config`
    *   Set the _Device state topic_ (may be hidden under _Advanced Options_) to `udmi_state`
4.  Install the Google Cloud SDK in order to be able to use some of the tools included, 
    such as the registrar tool
    *   Follow the guidance on `[Google Cloud SDK](https://cloud.google.com/sdk/docs/install) 
        instalation documentation
    *   Once installed, configure the 
        [application default credentials](https://cloud.google.com/sdk/gcloud/reference/auth/application-default) 
        using either an end-user credentials (`gcloud auth login`) or with a 
        service account (`gcloud auth activate-service-account`).
    *   Select the project your registry resides in using 
        `gcloud config set project <YOUR_PROJECT_ID>`

All the tools working with Google Cloud Platform (GCP) projects use GCP's
[application default credentials](https://cloud.google.com/sdk/gcloud/reference/auth/application-default)
model for authenticating interaction with the cloud. Depending on your setup,
this can be used with end-user credentials (`gcloud auth login`) or with a
service account (`gcloud auth activate-service-account`).