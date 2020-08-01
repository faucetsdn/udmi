# UDMI tools setup

## Baseline Documentation

Most interactions work with [Cloud IoT Core](https://cloud.google.com/iot/docs/) and
[PubSub](https://cloud.google.com/pubsub/docs), so a familiarity with those topics
is assumed throughout the UDMI documentation.

## Cloud Authentication

All the tools working with Google Cloud Platform (GCP) projects use GCP's
[application default credentials](https://cloud.google.com/sdk/gcloud/reference/auth/application-default)
model for authenticating interaction with the cloud. Depending on your setup,
this can be used with end-user credentials (`gcloud auth login`) or with a
service account (`gcloud auth activate-service-account`). 

## Software Pre-requisites

Some of the tools require JDK 11 or above. This will need to be manually installed
on your development system if it's not already there.
