[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Setup](#)

# UDMI Tools Setup

## Baseline Requirements

Most interactions work with [Cloud IoT Core](https://cloud.google.com/iot/docs/) 
and [PubSub](https://cloud.google.com/pubsub/docs), so a familiarity with those 
topics is assumed throughout the UDMI documentation.

## Software Prerequisites

As a minimum, to run or deploy the included tools, the following software will need 
to be installed on your development system if it's not already there.

*   _JDK v11_
*   _NPM_ & _Node JS_
*   _coreutils_
*   _jq_

## Cloud Prerequisites

To use the included functions, you will be required to install the Google Cloud SDK
and Firebase CLI. The [cloud setup guidance](../cloud/gcp/cloud_setup.md) and 
[dashboard setup guidance](../cloud/gcp/dashboard.md) provide additional 
guidance their installation.

*   [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) is required
    for GCP command-line utilities ('gcloud') 
*   _Firebase CLI_ is required to deploy the Firebase Dashboards and GCP Cloud Functions

All the tools working with Google Cloud Platform (GCP) projects use GCP's 
[application default credentials](https://cloud.google.com/sdk/gcloud/reference/auth/application-default) 
model for authenticating interaction with the cloud. Depending on your setup, 
this can be used with end-user credentials (`gcloud auth login`) or with a 
service account (`gcloud auth activate-service-account`).

## UDMI Site Model Workflow
The [recommended workflow](../guides/workflow.md) for UDMI covers using the _registrar_ and
_validator_ tools to configure and test a cloud project. Additionally, the _pubber_ tool
is instrumental in setting up and testing the system independent of actual device setup.
