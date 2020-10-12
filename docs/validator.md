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
* `PROJECT_ID`: The GCP project ID to validate against.
* `SITE_PATH`: A [site model](site_model.md) definition directory.
* `SUBSCRIPTION_ID`: A GCP PubSub subscription (manually setup by a project admin, as necessary).

## Regression Testing

The `bin/test_schema` script runs a regression suite of all schemas against all tests.
This must pass before any PR can be approved. If there is any failure, a bunch of diagnostic
information will be included about what exactly went wrong.
