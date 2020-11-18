# Metadata Description File

A device's `metadata.json` file provides the source template for what the
device is _supposed_ to be. It is used to provide the basic information for
registering devices in a IoT Core registry, generation of other template
files (e.g. _config_ block) associated with the device, and as a means for
runtime validation of what the device is actually sending.

The `metadata.json` files are stored in a `devices/{device_id}` directory
with an expected 1:1 mapping between device directory and device entry
in a IoT Core registry.

## System Tooling

There are three main sets of tools that rely on the `metadata.json` file,
all found in the UDMI `bin/` directory.
* [`genkeys`](keygen.md): Generates public/private device key pairs (optional as needed).
* [`registrar`](registrar.md): Validates metadata files and can (optionally) register devices.
* [`validator`](validator.md): Validates live data stream from devices.

## Output Files

The various tools will generate some output files in the same `devices/` directory:
* `rsa_*`: Device public/private key files (`genkeys` tool).
* `metadata_norm.json`: Normalized version of the `metadata.json` file (`registrar` tool).
* `generated_config.json`: Default device IoT Core `config` block (`registrar` tool).
* `errors.json`: Detailed error file (`registrar` tool).

## Metadata Structure

The structure of the metadata file is validated by the various tools,
and roughly corresponds to the following structure. See the various
working examples for more specific details on what these fields look like.

* `version`: UDMI version, currently always 1.
* `timestamp`: Timestamp that the metadata file was last updated.
* `hash`: Automatically generated field that contains the hash of file contents.
* `pointset`: Information pertaining to the points defined for the device.
  * `points/{point_name}`: Information about a specific point name of the device.
    * `units`: Expected unit configuration for the point.
* `system`: High-level sytem informaiton about the device.
  * `location`: Properties the expected physical location of the device.
  * `physical_tag`: Information used to print a physical QR code label.
* `cloud`: Information specific to how the device communicates with the cloud.
  * `auth_type`: Key type (e.g. `RS256`) used for cloud communication.
