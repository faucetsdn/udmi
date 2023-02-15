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
*   `coreutils`
*   `jq`

## Cloud Prerequisites

The tooling requires the he [UDMIS infrastructure](../cloud/gcp/udmis.md) to be deployed onto the target GCP project. 

[Google Cloud SDK](https://cloud.google.com/sdk/docs/install) is required for GCP command-line utilities ('gcloud') 
## UDMI Site Model Workflow
The [recommended workflow](../guides/workflow.md) for UDMI covers using the _registrar_ and
_validator_ tools to configure and test a cloud project. Additionally, the _pubber_ tool
is instrumental in setting up and testing the system independent of actual device setup.
