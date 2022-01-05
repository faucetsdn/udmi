[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./)
/ [Pubber](#)

# Pubber Reference Client

The _Pubber_ reference client is a sample implementation of a client-side 'device' that implements
the UDMI schema. It's not intended to be any sort of production-worthy code or library, rather just
a proof-of-concept of what needs to happen.

## Build Pubber

<pre>
~/daq$ <b>pubber/bin/build</b>
Running in /home/peringknife/daq/pubber

> Task :compileJava
&hellip;

BUILD SUCCESSFUL in 2s
2 actionable tasks: 1 executed, 1 up-to-date
</pre>

## Key Generation

<pre>
~/daq$ <b>bin/keygen RS256_X509 local</b>
Generating a 2048 bit RSA private key
............+++
......................................+++
writing new private key to 'local/rsa_private.pem'
-----
~/daq$ <b>ls -l local/rsa_*</b>
-rw-r--r-- 1 user primarygroup 1094 Nov 19 18:56 local/rsa_cert.pem
-rw------- 1 user primarygroup 1704 Nov 19 18:56 local/rsa_private.pem
-rw-r--r-- 1 user primarygroup 1216 Nov 19 18:56 local/rsa_private.pkcs8
</pre>

After generating the key pair, you'll have to upload/associate the `rsa_cert.pem` public certificate
with the device entry in the cloud console as an _RS256_cert_. (This can be done when the device is
created, or anytime after.)

## Configuration

The `local/pubber.json` file configures the key cloud parameters needed for operation
(the actual values in the file should match your GCP setup):
<pre>
~/daq$ <b>cat local/pubber.json</b>
{
  "projectId": "gcp-account",
  "cloudRegion": "us-central1",
  "registryId": "sensor_hub",
  "deviceId": "AHU-1"
}
</pre>

## Operation

<pre>
~/daq$ <b>pubber/bin/run</b>
[main] INFO daq.pubber.Pubber - Reading configuration from /home/user/daq/local/pubber.json
[main] INFO daq.pubber.Pubber - Starting instance for registry sensor_hub
[main] INFO daq.pubber.MqttPublisher - Creating new publisher-client for GAT-001
[main] INFO daq.pubber.MqttPublisher - Attempting connection to sensor_hub:GAT-001
[MQTT Call: projects/gcp-account/locations/us-central1/registries/sensor_hub/devices/GAT-001] INFO daq.pubber.Pubber - Received new config daq.udmi.Message$Config@209307c7
[MQTT Call: projects/gcp-account/locations/us-central1/registries/sensor_hub/devices/GAT-001] INFO daq.pubber.Pubber - Starting executor with send message delay 2000
[main] INFO daq.pubber.Pubber - synchronized start config result true
[MQTT Call: projects/gcp-account/locations/us-central1/registries/sensor_hub/devices/GAT-001] INFO daq.pubber.Pubber - Sending state message for device GAT-001
&hellip;
[pool-1-thread-1] INFO daq.pubber.Pubber - Sending test message for sensor_hub/GAT-001
[pool-1-thread-1] INFO daq.pubber.Pubber - Sending test message for sensor_hub/GAT-001
</pre>


## Cloud Setup

To use Pubber, there needs to be a cloud-side device entry configured in a GCP project configured to
use [Cloud IoT](https://cloud.google.com/iot/docs/). The
[Creating or Editing a Device](https://cloud.google.com/iot/docs/how-tos/devices#creating_or_editing_a_device)
section of the documentation describe how to create a simple device and key-pair (see next section for
a helper script). You can/should substitute the relevant values in the configuration below for your
specific setup. The relevant bits of configuration are the information in the <code>local/pubber.json</code>
file (see above), and the generated public key (also see above).

Alternatively, you can use the [registrar tool](registrar.md) to automate device registration.
