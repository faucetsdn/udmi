# Troubleshooting

## Validator 
When using the validator, error messages are provided when there is an issue
with the tool itself. These give some direction on how to resolve it
(e.g. missing parameters, missing components, authentication errors).

Authentication errors are frequently encountered when the user authenticated
onto the gcloud SDK does not have [sufficient permissions](cloud_setup.md) 
or the wrong GCP project is set.

If no error message is given, but there are no results or the expected results
are not observed, then the following troubleshooting steps are suggested to
identify the problem.

- Check the network the device is connected to has access to the internet
- Check the device is connecting to the internet (e.g. with a packet capture)
- Check the device is configured with is the expected device ID and registry
- Check IoT Core for device status - e.g. last connection
- Check device certificate, configuration, or local errors if not connecting
- Enable state/config history on IoT core, and check if any state messages have
  been sent or config messages received
- Enable debug logging for the device on IoT Core and check the Stackdriver log
  for any errors
- Ensure the [cloud functions have been deployed](dashboard.md) and the 
  [Pub/Sub subscriptions are configured](cloud_setup.md). For use with the 
  validator, the subscription should be to the udmi_target topic
- Check the Pub/sub subscription to check messages are being sent to the cloud 
- Check the payload is as expected
- Check device onboard logs (some devices may have a configurable debug level
  which must be set)


