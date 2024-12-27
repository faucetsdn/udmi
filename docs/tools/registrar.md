[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Registrar](#)

# Registrar Overview

The `registrar` is a utility program that registers and updates devices in Cloud IoT.
Running `bin/registrar` will validate local metadata and (optionally) register devices
in a cloud project.

See the [setup docs](setup.md) for common setup required for running this tool.

The [site model](../specs/site_model.md) documentation covers the basic structure of the
site-specific configuration. Ideally, this directory would be placed under
source control as a site-specific repo.

## Theory Of Operation

* The target set of _expected_ devices is determined from directory entries in
<code>_{site_path}_/devices/</code>.
* Existing devices that are not listed in the site config are blocked (as per
Cloud IoT device setting).
* Devices not found in the target registry are automatically created.
* Existing device registry entries are unblocked and updated with the new configuration.
* Various intermediate and summary files are written to the site directory. Typically,
these can be safely committed to source control as they are deterministic.

## Device Metadata & Keys

The expected model of a device is defined by a [device metadata](../specs/metadata.md) file,
along with a public key for that device (required for communication with the cloud).
Devices (that aren't proxied by a gateway) that do not auto-generate a public key
can use the [`bin/keygen`](keygen.md) utility to create a proper public/private key pair.

## Tool Execution

The registrar tool can be run locally to just validate the metadata, or with an optional
`project_id` to specify a target GCP project. Additional command-line arguments can
be used to specific specific device(s) to register (rather than all).

```
Usage:

bin/registrar site_model project_spec [options] [devices...]

bin/registrar site_spec [options] [devices...]
```

* `site_model`: The path to the _directory_ containing the site model, or a model-with-project _file_ directly.
* `project_spec`: The project ID that contains the target registry. The project ID can be prepended with iot_provider:
  * `//clearblade/PROJECT_ID` for a public ClearBlade project.
  * `//gbos/PROJECT_ID` for a Google operated ClearBlade project.
* `site_spec`: Path to a configuration file which contains configuration options;
* `options`: Various options to impact behavior:
  * `-a` Set alternate registry 
  * `-b` Block unknown devices.
  * `-c` Count of registries to be created
  * `-d` Delete all device in the site model from the registry (combine with `-x` to delete all devices from the registry)
  * `-e` Set registry suffix
  * `-f` Set PubSub feed topic
  * `-h` Show help and exit
  * `-l` Set idle limit
  * `-m` Initial metadata model out
  * `-n` Number of thread counts.
  * `-p` Set Project ID
  * `-q` Query only, registry to not be updated
  * `-r` Set tool root path
  * `-s` Set site path
  * `-t` Do not validate metadata
  * `-x` Delete unknown devices from the registry.
* `devices`: Multiple device entries for limited registration. Can be just the device name
  (`AHU-12`), or path to device (`site/devices/AHU-12`) for use with file-name glob.

Examples:

```
bin/registrar sites/UK-LON-ABC //gbos/bos-platform-testing 
bin/registrar sites/UK-LON-ABC/cloud_iot_config.json
```


Running the tool will create some output files for each device, and also a top-level
`registration_summary.json` file with summary results. Detailed error reports (if any)
for individual devices will be in their respective device directories.

<pre>
<b>~/udmi$ cat test_site/cloud_iot_config.json </b>
{
  "cloud_region": "us-central1",
  "site_name": "ZZ-TRI-FECTA",
  "registry_id": "registrar_test"
}
<b>~/udmi$ bin/registrar test_site/</b>
Building validator...
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: /home/user/udmi/validator/src/main/java/com/google/daq/mqtt/validator/Validator.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
Running tools version 1.0.0-2-ga413c4e
Using gcloud auth:
Your active configuration is: [udmi-test]
user@google.com
Using cloud project --
Using site config dir test_site/
Using schema root dir bin/../schema
Using device filter
java args -- test_site/ bin/../schema
Reading Cloud IoT config from /home/user/udmi/test_site/cloud_iot_config.json
Initializing with default credentials...
Jul 30, 2020 4:02:12 PM com.google.auth.oauth2.DefaultCredentialsProvider warnAboutProblematicCredentials
WARNING: Your application has authenticated using end user credentials from Google Cloud SDK. We recommend that most server applications use service accounts instead. If your application continues to use end user credentials from Cloud SDK, you might receive a "quota exceeded" or "API not enabled" error. For more information about service accounts, see https://cloud.google.com/docs/authentication/.
Created service for project projects/--/locations/us-central1
Working with project -- registry us-central1/registrar_test
Loading local device GAT-123
Loading local device AHU-22
Loading local device SNS-4
Loading local device AHU-1
Skipping remote registry fetch
Processed 4 devices
Removing test_site/devices/GAT-123/errors.map
Removing test_site/devices/AHU-1/errors.map
Removing test_site/devices/AHU-22/errors.map
Removing test_site/devices/SNS-4/errors.map

Summary:
  Device Clean: 4
Out of 4 total.
Done with PubSubPusher
Registrar complete, exit 0
<b>~/udmi$ cat test_site/registration_summary.json </b>
{
  "Clean" : {
    "AHU-1" : "True",
    "AHU-22" : "True",
    "GAT-123" : "True",
    "SNS-4" : "True"
  }
}
</pre>


## Sequence Diagram

Expected workflow to configure a registry using Registrar:

* `Device`: Target IoT Device
* `Local`: Local clone of site configuration repo
* `Registrar`: This utility program
* `Registry`: Target Cloud IoT Core registry
* `Repo`: Remote site configuration repo

All operations are manual except those involving the `Registrar` tool.

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
