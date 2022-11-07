[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Validator](#)

# Validator Setup

The `validator` is a sub-component of DAQ that can be used to validate JSON files or stream
against a schema defined by the standard [JSON Schema](https://json-schema.org/) format along
with additional constraints stipulated by the UDMI standard.

There are several different ways to run the validator depending on the specific objective:
* PubSub Stream Validation
* Regression Testing
* (TODO: Fix _local device sample_ validation)

See the [setup docs](setup.md) for common setup required for running this tool.

## PubSub Stream Validation

PubSub stream validation works against a live data stream pulled from a pre-existing subscription.
`bin/validator` takes three arguments:
* `SITE_PATH`: A [site model](../specs/site_model.md) definition directory.
* `PROJECT_ID`: The GCP project ID to validate against.
* `SUBSCRIPTION_ID`: A GCP PubSub subscription (manually setup by a GCP project admin).

This program will endlessly consume messages from the given PubSub subsubscription and validate the messages,
writing the results in a `out/validation_report.json` summary file. For example:
<pre>
~/udmi$ <b>timeout 5m bin/validator  ../sites/zz-sin-fins/ udmi-testing username-debug</b>
...
<em>wait 5 minutes for completion</em>
udmi$ <b>ls -l out/validation_report.json</b>
-rw-r--r-- 1 username primarygroup 168311 Jan 28 00:32 out/validation_report.json
</pre>

## Regression Testing

The `bin/test_schema` script runs a regression suite of all schemas against all tests.
This must pass before any PR can be approved. If there is any failure, a bunch of diagnostic
information will be included about what exactly went wrong.

## Project Exceptions

In cases where there's an issue that should be excluded form being an error in the validator
output, it can be marked as an _exception_ by including an [exceptions.json](exceptions.json)
file in the appropriate site directory. For given entity type prefixes (`AHU-`) a set of
reg-ex matches can be used to exclude specific error cases.

## Error Output

If there is an error validating a particular device, a warning/error message
will be shown on the console and diagnostic files will be saved in
`{udmi_root}/out/devices/{device_id}/`. A typical error could be a mismatch
between the points published by the device and what was expected from it, as
defined in the device's [metadata.json](../specs/metadata.md) file.
Example run showing a device publishing points not in its metadata:

```
~/sites/gcp-project$ ~/udmi/bin/validator . gcp-project udmi-validator
...
Resetting existing subscription projects/gcp-project/subscriptions/udmi-validator
Entering pubsub message loop on projects/gcp-project/subscriptions/udmi-validator
Processing device #1/665: PS-20/pointset
java.lang.RuntimeException: Metadata validation failed: Extra points: faulty_finding,recalcitrant_angle,superimposition_reading
	at com.google.daq.mqtt.validator.ReportingDevice.validateMetadata(ReportingDevice.java:65)
	at com.google.daq.mqtt.validator.Validator.validateUpdate(Validator.java:304)
	at com.google.daq.mqtt.validator.Validator.validateMessage(Validator.java:202)
	at com.google.daq.mqtt.validator.Validator.lambda$validatePubSub$0(Validator.java:185)
	at com.google.daq.mqtt.util.PubSubUdmiClient.processMessage(PubSubClient.java:105)
	at com.google.daq.mqtt.validator.Validator.validatePubSub(Validator.java:184)
	at com.google.daq.mqtt.validator.Validator.main(Validator.java:93)
Validation complete PS-20/pointset
```

Which will result in detailed output files:
```
~/sites/site_name$ ls -l ~/udmi/out/devices/PS-20/
total 24
-rw-r--r-- 1 username primarygroup 233 Oct 14 07:48 pointset.attr
-rw-r--r-- 1 username primarygroup 324 Oct 14 07:48 pointset.json
-rw-r--r-- 1 username primarygroup 328 Oct 14 07:48 pointset.out
-rw-r--r-- 1 username primarygroup 231 Oct 14 07:48 system.attr
-rw-r--r-- 1 username primarygroup 274 Oct 14 07:48 system.json
-rw-r--r-- 1 username primarygroup 127 Oct 14 07:48 system.out
```

## Advanced Usage

There's other options available for validation, but they aren't completely documented. The `bin/validator`
script interally calls `validator/bin/validate` with a number of additional arguments/options. Notably,
there is an alternative to PubSub stream validation that uses a shadow registry for GCP exchanges (easier
authentication).

```
~/udmi/validator/bin/validate bacnet-gateway schema reflect GAT-4128276 us-mtv-918r --
```

## Troubleshooting
 
When using the validator, error messages are provided when there is an issue
with the tool itself. These give some direction on how to resolve it
(e.g. missing parameters, missing components, authentication errors).

Authentication errors are frequently encountered when the user authenticated
onto the gcloud SDK does not have [sufficient permissions](../cloud/gcp/cloud_setup.md) 
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
- Ensure the [cloud functions have been deployed](../cloud/gcp/udmis.md), the cloud functions
  are running with a service account with [sufficient permissions](../cloud/gcp/cloud_setup.md)  
  and the [Pub/Sub subscriptions are configured](../cloud/gcp/cloud_setup.md). For use with the 
  validator, the subscription should be to the udmi_target topic. 
- Check the Pub/sub subscription to check messages are being sent to the cloud 
- Check the payload is as expected

### Common Errors

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
  permissions](../cloud/gcp/cloud_setup.md)  
- Ensure subscribing to the right topic
