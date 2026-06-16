[**UDMI**](../../) / [**Docs**](../) / [Tools](#)

# Common Arguments

Most tools take a [site model](../specs/site_model.md) as the first argument.
Many tools take a [project spec](project_spec.md) as the second argument.

# Tools

- [keygen](keygen.md) - a script to generate an RSA or ES key for single devices
- [pagent](pagent.md) - a tool for automated cloud provisioning of devices (GCP)
- [pubber](pubber.md) - a sample implementation of a client-side 'device' that implements the UDMI schema
- [registrar](registrar.md) - a utility to register and updates devices in Cloud IoT Core (GCP)
- [registrar_service](registrar_service.md) - a wrapper on top of the registrar utility to enable automated triggering of registrar on source repo updates
- [reset_config](reset_config.md) - a utility to send a config messages to devices
- [sequencer](sequencer.md) - a utility to validate device [sequences](../specs/sequences/) (GCP)
- [validator](validator.md) - a utility for validating messages (GCP)
- [gittools](gittools.md) - collection of utilities for working with git branches
- [gcloud](gcloud.md) - various tips and tricks for working with gcloud on GCP
- [bambi backend service](bambi_service.md) - a service to use Google Sheets as an interface to manage updates to the site model source repository 
- [reconciler_service](reconciler_service.md) - a service to automatically open pull requests from proposal branches in the source repository

## Setup

- [Setup instructions (GCP)](setup.md) are provided to set up the local environment for using tools.
- [sharding](sharding.md) - ability to run sequencer in shards for parallelism
- [Output streaming](stream_to_gsheet.md) can be enabled to stream output from any tool to a Google Sheet
