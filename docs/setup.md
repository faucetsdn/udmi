# UDMI tools setup


## Overview
Illustrative Message Walk.md describes the different components of the tools

## Baseline Documentation

Most interactions work with [Cloud IoT Core](https://cloud.google.com/iot/docs/) and
[PubSub](https://cloud.google.com/pubsub/docs), so a familiarity with those topics
is assumed throughout the UDMI documentation.

## Software Pre-requisites
To run or deploy the included tools, the following software will need to be installed on your development system if it's not already there.
* `JDK v11`
* `NPM & Node JS`
* `coreutils`
* `jq`

## Cloud Pre-requisites

* `[Google Cloud SDK](https://cloud.google.com/sdk/docs/install) is required
for GCP command-line utilities ('gcloud') cloud_setup.md
* `Firebase CLI` is required to deploy the Firebase Dashboards and GCP Cloud Functions

All the tools working with Google Cloud Platform (GCP) projects use GCP's

model for authenticating interaction with the cloud. Depending on your setup,
this can be used with end-user credentials (`gcloud auth login`) or with a
service account (`gcloud auth activate-service-account`).

## UDMI Site Model Workflow
workflow.md

he [recommended workflow](docs/workflow.md) for UDMI covers using the _registrar_ and
_validator_ tools to configure and test a cloud project. Additionally, the _pubber_ tool
is instrumental in setting up and testing the system independent of actual device setup.



