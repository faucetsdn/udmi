# Validator Setup

The `validator` is a sub-component of DAQ that can be used to validate JSON files or stream against a schema
defined by the standard [JSON Schema](https://json-schema.org/) format. The validator does not itself specify
any policy, i.e. which schema to use when, rather just a mechanism to test and validate.

The "schema set" is a configurable variable, and the system maps various events to different sub-schemas within
that set. Direct file-based validations run against an explicitly specified sub-schema, while the dynamic PubSub
validator dynamically chooses the sub-schema based off of message parameters. There's currently two schemas
available, defined in the `schemas/` subdirectory:
* `simple`, which is really just there to make sure the system works.
* [`UDMI`](../schemas/udmi/README.md), which is a building-oriented schema for data collection.

## Validation Mechanisms

There are several different ways to run the validator depending on the specific objective:
* Local File Validation
* Integration Testing
* PubSub Stream Validation

### Local File Validation

Local file validation runs the code against a set of local schemas and inputs. The example below shows
validating one schema file against one specific test input.
Specifying a directory, rather than a specific schema or input, will run against the entire set.
An output file is generated that has details about the schema validation result.

<pre>
~/daq$ <b>validator/bin/validate schemas/simple/simple.json schemas/simple/simple.tests/example.json</b>
Executing validator schemas/simple/simple.json schemas/simple/simple.tests/example.json...
Running schema simple.json in /home/user/daq/schemas/simple
Validating example.json against simple.json
Validation complete, exit 0
~/daq$
</pre>

### Integration Testing

The `validator/bin/test` script runs a regression suite of all schemas against all tests.
This must pass before any PR can be approved. If there is any failure, a bunch of diagnostic
information will be included about what exactly went wrong.

<pre>
~/daq/validator$ <b>bin/test</b>

BUILD SUCCESSFUL in 3s
2 actionable tasks: 2 executed

BUILD SUCCESSFUL in 3s
2 actionable tasks: 2 executed
Validating empty.json against config.json
Validating errors.json against config.json
<em>&hellip;</em>
Validating example.json against state.json
Validating error.json against simple.json
Validating example.json against simple.json

Done with validation.
</pre>

### PubSub Stream Validation

Validating a live PubSub stream requires more setup, but ultimately most closely reflects what an
actual system would be doing during operation. The [DAQ PubSub Documentation](pubsub.md) details
how to set this up. It uses the same underlying schema files as the techniques above, but routes
it though a live stream in the cloud.

Streaming validation validates a stream of messages pulled from a GCP PubSub topic.
There are three configuration values required in the `local/system.yaml` file to make it work:
* `gcp_cred`: The service account credentials, as per the general [DAQ Firebase setup](firebase.md).
* `gcp_topic`: The _PubSub_ (not MQTT) topic name.
* `schema_path`: Indicates which schema to validate against.

You will need to add full Project Editor permissions for the service account.
E.g., to validate messages on the `projects/gcp-account/topics/telemetry` topic,
there should be something like:

<pre>
~/daq$ <b>fgrep gcp_ local/system.conf</b>
gcp_cred=local/gcp-project-ce6716521378.json
gcp_topic=telemetry
schema_path=schemas/abacab/
</pre>

Running `bin/validate` will parse the configuration file and automatically start
verifying PubSub messages against the indicated schema.
The execution output has a link to a location in the Firestore setup
where schema results will be stored, along with a local directory of results.

<pre>
~/daq$ <b>bin/validate</b>
Using credentials from /home/user/daq/local/gcp-project-ce6716521378.json

BUILD SUCCESSFUL in 3s
2 actionable tasks: 2 executed
Executing validator /home/user/daq/validator/schemas/abacab/ pubsub:telemetry_topic...
Running schema . in /home/user/daq/validator/schemas/abacab
Ignoring subfolders []
Results will be uploaded to https://console.cloud.google.com/firestore/data/registries/?project=gcp-project
Also found in such directories as /home/user/daq/validator/schemas/abacab/out
Connecting to pubsub topic telemetry
Entering pubsub message loop on projects/gcp-project/subscriptions/daq-validator
Success validating out/pointset_FCU_09_INT_NE_07.json
Success validating out/pointset_FCU_07_EXT_SW_06.json
Error validating out/logentry_TCE01_01_NE_Controls.json: DeviceId TCE01_01_NE_Controls must match pattern ^([a-z][_a-z0-9-]*[a-z0-9]|[A-Z][_A-Z0-9-]*[A-Z0-9])$
Success validating out/logentry_FCU_01_NE_08.json
Error validating out/pointset_TCE01_01_NE_Controls.json: DeviceId TCE01_01_NE_Controls must match pattern ^([a-z][_a-z0-9-]*[a-z0-9]|[A-Z][_A-Z0-9-]*[A-Z0-9])$
Success validating out/logentry_FCU_01_SE_04.json
<em>&hellip;</em>
</pre>

## Site Validation

Following on from individual-device validation, it is possible to validate against an entire building model
This is a WIP provisional feature. But, roughly speaking, it looks like this:

<pre>
~/daq$ export GOOGLE_APPLICATION_CREDENTIALS=local/essential-monkey.json
~/daq$ <b>validator/bin/validate schemas/udmi pubsub:<i>topic</i> <i>dev</i> <i>site_model/<i/></b>
</pre>

* `schemas/udmi` is the schema to validate against.
* `pubsub:topic` points to the pub-sub topic stream to validate.
* `dev` is an arbitrary designator for running different clients against the same project.
* `site_model/` is a directory containing the requisite building model.

Output from a site validation run will be in `validations/metadata_report.json`.

### Types and Topics

When using the
[GCP Cloud IoT Core MQTT Bridge](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#publishing_telemetry_events)
there are multiple ways the subschema used during validation is chosen.
* All messages have their attributes validated against the `.../attributes.json` schema. These attributes are
automatically defined server-side by the MQTT Client ID and Topic, and are not explicitly included in any message payload.
* A [device event message](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#publishing_telemetry_events)
is validated against the sub-schema indicated by the MQTT topic `subFolder`. E.g., the MQTT
topic `/devices/{device-id}/events/pointset` will be validated against `.../pointset.json`.
* [Device state messages](https://cloud.google.com/iot/docs/how-tos/config/getting-state#reporting_device_state)
are validated against the `.../state.json` schema on `/devices/{device-id}/state` MQTT topic.
* (There currently is no stream validation of
[device config messages](https://cloud.google.com/iot/docs/how-tos/config/configuring-devices#mqtt), which are sent on the
`/devices/{device-id}/config` topic.)

See this handy-dandy table:

| Type     | Category | subFolder |                MQTT Topic              |  Schema File  |
|----------|----------|-----------|----------------------------------------|---------------|
| state    | state    | _n/a_     | `/devices/{device_id}/state`           | state.json    |
| config   | config   | _n/a_     | `/devices/{device-id}/config`          | config.json   |
| pointset | event    | pointset  | `/devices/{device-id}/events/pointset` | pointset.json |
| logentry | event    | logentry  | `/devices/{device-id}/events/logentry` | logentry.json |
