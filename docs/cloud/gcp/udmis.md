[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [UDMIS](#)

# UDMIS

UDMIS (Universal Device Management Interface Services) is a cloud infrastructure which comprises cloud functions. These process messages according to the [sub-blocks API](../../specs/subblocks.md). This is required for the enablement of other applications and tooling e.g. [validation tooling](../../tools/readme.md).

# Deployment

`udmis/deploy_udmis_gcloud PROJECT_ID [options]`

Note: `gcloud` must be installed locally, and authenticated with normal credentials `gcloud auth login`

A `--service_account="SERVICE_ACCOUNT"` argument can be provided to deploy cloud functions with the given service account if the cloud environment does not permit use of the App Engine default service account.
