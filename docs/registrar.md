# Registrar Overview

The `registrar` is a utility program that registers and updates devies in Cloud IoT.
Running `bin/registrar` will pull the necessary configuraiton values from `local/system.conf`,
build the executable, and register/update devices.

## Configuration

The `local/system.conf` file should have the following parameters (in `x=y` syntax):
* `gcp_cred`: Defines the target project and [service account](service.md) to use for configuration.
* `site_path`: [Site-specific configuration](site_path.md) for the devices that need to be registered.
* `schema_path`: Path to metadata schema (see the [DAQ PubSub documentation](pubsub.md) for more details/examples).

The target `gcp_cred` service account will need the _Cloud IoT Provisioner_ and _Pub/Sub Publisher_ roles.
There also needs to be an existing `registrar` topic (or as configured in `cloud_iot_config.json`, below).

## Theory Of Operation

* The target set of _expected_ devices is determined from directory entries in
<code>_{site_path}_/devices/</code>.
* Existing devices that are not listed in the site config are blocked (as per
Cloud IoT device setting).
* If a device directory does not have an appropriate key, one will be automaticaly generated.
* Devices not found in the target registry are automatically created.
* Existing device registy entries are unblocked and updated with the appropriate keys.

## Device Settings

When registering or updating a device, the Registrar manipulates a few key pieces of device
information:
* Auth keys: Public authentiation keys for the device.
* Metadata: Various information about a device (e.g. site-code, location in the building).

This information is sourced from a few key files:

* `{site_dir}/cloud_iot_config.json`:
Cloud project configuration parameters (`registry_id`, `cloud_region`, etc...).
* `{site_dir}/devices/{device_id}/metadata.json`:
Device metadata (e.g. location, key type).
* `{site_dir}/devices/{device_id}/rsa_private.pem`:
Generated private key for device (used on-device).

## Sample Output

The produced `registration_summary.json` document provides an overview of the analyzed files,
clearly any errors that should be addressed for full spec compliance. Additionaly, an
`errors.json`

<pre>
user@machine:~/daq$ <b>cat local/site/cloud_iot_config.json </b>
{
  "cloud_region": "us-central1",
  "site_name": "SG-MBC2-B80",
  "registry_id": "iotRegistry",
  "registrar_topic": "registrar"
}
user@machine:~/daq$ <b>bin/registrar daq-testing</b>
Activating venv
Flattening config from local/system.yaml into inst/config/system.conf
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Running tools version 1.5.1-16-g9ed5861
Using cloud project bos-daq-testing
Using site config dir local/site
Using schema root dir schemas/udmi
Using device filter
Reading Cloud IoT config from /home/user/daq/local/site/cloud_iot_config.json
Initializing with default credentials...
Jun 12, 2020 1:24:37 PM com.google.auth.oauth2.DefaultCredentialsProvider warnAboutProblematicCredentials
WARNING: Your application has authenticated using end user credentials from Google Cloud SDK. We recommend that most server applications use service accounts instead. If your application continues to use end user credentials from Cloud SDK, you might receive a "quota exceeded" or "API not enabled" error. For more information about service accounts, see https://cloud.google.com/docs/authentication/.
Created service for project bos-daq-testing
Working with project bos-daq-testing registry iotRegistry
Loading local device AHU-1-1
Loading local device AHU-1-2
Fetching remote registry iotRegistry
Updated device entry AHU-1-1
Sending metadata message for AHU-1-1
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by com.google.protobuf.UnsafeUtil (file:/home/user/daq/validator/build/libs/validator-1.0-SNAPSHOT-all.jar) to field java.nio.Buffer.address
WARNING: Please consider reporting this to the maintainers of com.google.protobuf.UnsafeUtil
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
Updated device entry AHU-1-2
Sending metadata message for AHU-1-2
Processed 2 devices
Updating local/site/devices/AHU-1-1/errors.json
Updating local/site/devices/AHU-1-2/errors.json

Summary:
  Device Envelope: 2
  Device Key: 1
  Device Validating: 2
Out of 2 total.
Done with PubSubPusher
Registrar complete, exit 0
user@machine:~/daq$ <b>cat local/site/registration_summary.json </b>
{
  "Envelope" : {
    "AHU-1-1" : "java.lang.IllegalStateException: Validating envelope AHU-1-1",
    "AHU-1-2" : "java.lang.IllegalStateException: Validating envelope AHU-1-2"
  },
  "Key" : {
    "AHU-1-2" : "java.lang.RuntimeException: Duplicate credentials found for AHU-1-1 & AHU-1-2"
  },
  "Validating" : {
    "AHU-1-1" : "org.everit.json.schema.ValidationException: #: 43 schema violations found",
    "AHU-1-2" : "org.everit.json.schema.ValidationException: #: 43 schema violations found"
  }
}
user@machine:~/daq$ <b>head local/site/devices/AHU-1-1/errors.json </b>
Exceptions for AHU-1-1
  Validating envelope AHU-1-1
    #/deviceId: string [AHU-1-1] does not match pattern ^[A-Z]{2,6}-[1-9][0-9]{0,2}$
  #: 43 schema violations found
    #/pointset/points: 40 schema violations found
      #/pointset/points/chilled_return_water_temperature_sensor/units: °C is not a valid enum value
      #/pointset/points/chilled_supply_water_temperature_sensor/units: °C is not a valid enum value
      #/pointset/points/chilled_water_valve_percentage_command/units: % is not a valid enum value
</pre>

## Sequence Diagram

Expected workflow to configure a registry using Registrar:

* `Device`: Target IoT Device
* `Local`: Local clone of site configuration repo
* `Registrar`: This utility program
* `Registry`: Target Cloud IoT Core registry
* `Repo`: Remote site configuration repo

All operations are manaul except those involving the `Registrar` tool.

<pre>
+---------+                +-------+                 +-----------+                 +-----------+ +-------+
| Device  |                | Local |                 | Registrar |                 | Registry  | | Repo  |
+---------+                +-------+                 +-----------+                 +-----------+ +-------+
     |                         |                           |                             |           |
     |                         |                           |                       Pull repo locally |
     |                         |<--------------------------------------------------------------------|
     |                         |    ---------------------\ |                             |           |
     |                         |    | Run Registrar tool |-|                             |           |
     |                         |    |--------------------| |                             |           |
     |                         |                           |                             |           |
     |                         | Read device configs       |                             |           |
     |                         |-------------------------->|                             |           |
     |                         |                           |                             |           |
     |                         |                           |            Read device list |           |
     |                         |                           |<----------------------------|           |
     |                         |                           |                             |           |
     |                         |           Write auth keys |                             |           |
     |                         |<--------------------------|                             |           |
     |                         |                           |                             |           |
     |                         |                           | Update device entries       |           |
     |                         |                           |---------------------------->|           |
     |                         |   ----------------------\ |                             |           |
     |                         |   | Registrar tool done |-|                             |           |
     |                         |   |---------------------| |                             |           |
     |                         |                           |                             |           |
     |     Install private key |                           |                             |           |
     |<------------------------|                           |                             |           |
     |                         |                           |                             |           |
     |                         | Push changes              |                             |           |
     |                         |-------------------------------------------------------------------->|
     |                         |                           |                             |           |
</pre>

### Source

Use with [ASCII Sequence Diagram Creator](https://textart.io/sequence#)

<pre>
object Device Local Registrar Registry Repo
Repo -> Local: Pull repo locally
note left of Registrar: Run Registrar tool
Local -> Registrar: Read device configs
Registry -> Registrar: Read device list
Registrar -> Local: Write auth keys
Registrar -> Registry: Update device entries
note left of Registrar: Registrar tool done
Local -> Device: Install private key
Local -> Repo: Push changes
</pre>
