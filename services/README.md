## BAMBI: BOS Automated Management Building Interface - Backend Service

This document provides instructions for running and managing the BAMBI backend service.

### Running the BAMBI Service

If running locally, first refresh your gcloud credentials:
```shell
gcloud auth login
gcloud auth application-default login \
--scopes="openid,https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets"
```

To start the BAMBI service, navigate to your UDMI root directory and execute the following command:

```shell
services/bin/bambi_service [--local] [--no-check] 
```
* --local: Run the service in a local environment.
* --no-check: Skip the check and creation of required Pub/Sub topics and subscriptions. Use this option if these resources are already configured.


### Standalone Import/Merge Functionalities
We can import and merge site models independently of the main service.
```shell
cd ${UDMI_ROOT}
source etc/shell_common.sh

# write to BAMBI 
sync_disk_site_model_to_bambi <spreadsheet_id> <path_to_site_model>

# write to disk
sync_bambi_site_model_to_disk <spreadsheet_id> <path_to_site_model>
```

### Building and Pushing the BAMBI Container
Use bin/container as done for other modules.
```shell
bin/set_project gcp_project[/udmi_namespace]
cd ${UDMI_ROOT}
bin/container services { prep, build, push, apply } [--no-check] [repo]
```

