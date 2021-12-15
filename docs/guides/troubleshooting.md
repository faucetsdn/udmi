# Troubleshooting

## Validator 
When using the validator, error messages are provided when there is an issue
with the tool itself. These give some direction on how to resolve it
(e.g. missing parameters, missing components, authentication errors).

Authentication errors are frequently encountered when the user authenticated
onto the gcloud SDK does not have [sufficient permissions](..//cloud/gcp/cloud_setup.md) 
or the wrong GCP project is set.

If no error message is given, but there are no results or the expected results
are not observed, then the following troubleshooting steps are suggested to
identify the problem.

- Check the network the device is connected to has access to the internet
- Check the device is connecting to the internet (e.g. with a packet capture)
- Check the device is configured with is the expected device ID and registry
- Check IoT Core for device status - e.g. last connection
- Check local device certificate, configuration, or local errors if not connecting. 
  Some devices may have a configurable debug level which must be enabled
- Enable state/config history on IoT core, and check if any state messages have
  been sent or config messages received
- Enable debug logging for the device on IoT Core and check the Stackdriver log
  for any errors
- Ensure the [cloud functions have been deployed](..//cloud/gcp/dashboard.md), the cloud functions
  are running with a service account with [sufficient permissions](..//cloud/gcp/cloud_setup.md)  
  and the [Pub/Sub subscriptions are configured](..//cloud/gcp/cloud_setup.md). For use with the 
  validator, the subscription should be to the udmi_target topic. 
- Check the Pub/sub subscription to check messages are being sent to the cloud 
- Check the payload is as expected

## Common Errors
```
Caused by: java.io.IOException: The Application Default Credentials are not available. They are available if running in Google Compute Engine. Otherwise, the environment variable GOOGLE_APPLICATION_CREDENTIALS must be defined pointing to a file defining the credentials. See https://developers.google.com/accounts/docs/application-default-credentials for more information.
```
- Ensure GCP Cloud SDK has correctly been setup. For further guidance refer to [GCP Cloud Documentation](https://cloud.google.com/docs/authentication/production)
- Ensure the validator tools are not running under sudo 

```
Processing device #1/12: XXX-1/event_unknown
Unknown schema subFolder 'event_unknown' for XXX-1
```
- Ensure the subscription used for the validator is to the `udmi_target` topic,
  and not the `udmi_state` topic or any others

**Missing messages** _or_ **messages not appearing in validators**
- Ensure the cloud functions are running with a service account with [sufficient
  permissions](cloud_setup.md)  
- Ensure subscribing to the right topic
