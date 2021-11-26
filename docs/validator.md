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
* `SITE_PATH`: A [site model](site_model.md) definition directory.
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

If there is an error validating a particular device, a warning/error message will be shown
on the console and diagnostic files will be saved in `{udmi_root}/out/devices/{device_id}/`.
A typical error could be a mismatch between the points published by the device
and what was expected from it, as defined in the device's [metadata.json](metadata.md) file.
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
	at com.google.daq.mqtt.util.PubSubClient.processMessage(PubSubClient.java:105)
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
