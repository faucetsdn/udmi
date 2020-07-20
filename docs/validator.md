# Validator Setup

The `validator` is a sub-component of DAQ that can be used to validate JSON files or stream
against a schema defined by the standard [JSON Schema](https://json-schema.org/) format along
with additional constraints stipulated by the UDMI standard.

There are several different ways to run the validator depending on the specific objective:
* PubSub Stream Validation
* Local File Validation
* Regression Testing

## PubSub Stream Validation

PubSub stream validation works against a live data stream pulled from a pre-existing subscription.
It requres a few pieces of information to work:
* `PROJECT_ID`: The GCP project ID to validate against.
* `SITE_PATH`: A directory structure containing a valid `cloud_iot_config.json` file (see below).
* `SUBSCRIPTION_ID`: A GCP PubSub subscription (manually setup by project admin, if necessary).

The [`cloud_iot_config.json`](cloud_iot_config.md) file contains a few key pieces of
information necessary for the tool, and generally indicates the root of a
site-specific directory (e.g., as stored in its own independent git repo).

## Local File Validation

Local file validation runs the code against a set of local schemas and inputs. The example below
shows validating one schema file against one specific test input.
Specifying a directory, rather than a specific schema or input, will run against the entire set.
An output file is generated that has details about the schema validation result.

_TBD_

## Regression Testing

The `bin/test_schema` script runs a regression suite of all schemas against all tests.
This must pass before any PR can be approved. If there is any failure, a bunch of diagnostic
information will be included about what exactly went wrong.
