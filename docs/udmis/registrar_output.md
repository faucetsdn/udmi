[**UDMI**](../../) / [**Docs**](../) / [**UDMIS**](.) / [Registrar Output](#)

Command for running registrar in a docker container:
```
docker run --rm --net udminet --name registrar -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/registrar site/ //mqtt/udmis
```

Sample output:
```
Unable to find image 'ghcr.io/faucetsdn/udmi:validator-latest' locally
validator-latest: Pulling from faucetsdn/udmi
ec99f8b99825: Already exists 
8abafddf1ae3: Pull complete 
f9eaaae8e4dc: Pull complete 
5a2589c2150b: Pull complete 
08b6c630112a: Pull complete 
0a5909cd013c: Pull complete 
eec9bf907dcf: Pull complete 
93a71d99e987: Pull complete 
31d56e6becc1: Pull complete 
Digest: sha256:af97be37493df358aec09650715ed9f11c649cfb7bebcee0529ee1ecf00b8dca
Status: Downloaded newer image for ghcr.io/faucetsdn/udmi:validator-latest
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
udmi version unknown
date: unrecognized option: iso=s
BusyBox v1.36.1 (2024-06-10 07:11:47 UTC) multi-call binary.

Usage: date [OPTIONS] [+FMT] [[-s] TIME]

Display time (using +FMT), or set time

	-u		Work in UTC (don't convert to local time)
	[-s] TIME	Set time to TIME
	-d TIME		Display TIME, not 'now'
	-D FMT		FMT (strptime format) for -s/-d TIME conversion
	-r FILE		Display last modification time of FILE
	-R		Output RFC-2822 date
	-I[SPEC]	Output ISO-8601 date
			SPEC=date (default), hours, minutes, seconds or ns

Recognized TIME formats:
	@seconds_since_1970
	hh:mm[:ss]
	[YYYY.]MM.DD-hh:mm[:ss]
	YYYY-MM-DD hh:mm[:ss]
	[[[[[YY]YY]MM]DD]hh]mm[.ss]
	'date TIME' form accepts MMDDhhmm[[YY]YY][.ss] instead
starting run at
java -cp /root/validator/build/libs/validator-1.0-SNAPSHOT-all.jar com.google.daq.mqtt.util.Dispatcher registrar site/ //mqtt/udmis
Writing reconciled configuration file to /root/out/registrar_conf.json
Using reflector iot client
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Loaded key /root/site/reflector/rsa_private.pkcs8 as sha256 42a8a8f1287aa775
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA token expiration sec 3600
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Using hash-key username/password /r/UDMI-REFLECT/d/ZZ-TRI-FECTA 42a8a8f1
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - CA cert file: /root/site/reflector/ca.crt
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Device cert file: /root/site/reflector/rsa_private.crt
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Private key file: /root/site/reflector/rsa_private.pem
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Password sha256 0049165a
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA creating client /r/UDMI-REFLECT/d/ZZ-TRI-FECTA on ssl://udmis:8883
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA creating new auth token for audience udmis
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Using hash-key username/password /r/UDMI-REFLECT/d/ZZ-TRI-FECTA 42a8a8f1
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA connecting to mqtt server ssl://udmis:8883
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 1 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/config
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 1 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/errors
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 0 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/commands/#
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA done with setup connection
Subscribed to mqtt/udmis/us-central1/UDMI-REFLECT/ZZ-TRI-FECTA
Starting initial UDMI setup process
Ignoring initial config received timeout (config likely empty)
Setting state version unknown timestamp 2024-07-19T04:20:12Z
UDMI setting reflectorState: {
  "version" : "unknown",
  "udmi" : {
    "setup" : {
      "transaction_id" : "RC:d22034.0001"
    }
  },
  "timestamp" : "2024-07-19T04:20:12Z"
}
UDMI received reflectorConfig: {
  "last_state" : "2024-07-19T04:20:12Z",
  "setup" : {
    "hostname" : "3d18e014852d",
    "functions_min" : 13,
    "functions_max" : 13,
    "udmi_version" : "1.4.2-248-gfeb3779c",
    "udmi_ref" : "ghcr.io/grafnu/udmi:udmis-gfeb3779cf",
    "built_at" : "2024-07-19T04:12:00Z",
    "built_by" : "testuser@testuser",
    "transaction_id" : "RC:d22034.0001"
  }
}
UDMI matching against expected state timestamp 2024-07-19T04:20:12Z
UDMI version mismatch: unknown
UDMI functions support versions 13:13 (required 13)
Created service for project udmis
Working with project udmis registry us-central1/ZZ-TRI-FECTA
Loading site_defaults.json
Finished loading 4 local devices.
Writing normalized /root/site/devices/GAT-123/out/metadata_norm.json
Writing normalized /root/site/devices/AHU-22/out/metadata_norm.json
Writing normalized /root/site/devices/AHU-1/out/metadata_norm.json
Writing normalized /root/site/devices/SNS-4/out/metadata_norm.json
Fetching devices from registry ZZ-TRI-FECTA...
Fetched 0 device models from cloud registry
Processing 4 new devices...
Waiting for device processing...
Waiting 61s for 4 tasks to complete...
Processed SNS-4 (4/4) in 0.045s (add)
Processed AHU-22 (3/4) in 0.046s (add)
Processed GAT-123 (1/4) in 0.091s (add)
Processed AHU-1 (2/4) in 0.160s (add)
Processed 4 (skipped 0) devices in 0.805s, 0.201s/d
Updating 0 existing devices...
Waiting for device processing...
Processed 0 (skipped 0) devices in 0.000s, 0.000s/d
Finished registering 4/4 devices.
Binding devices to GAT-123, already bound: 
Binding 2 unbound devices to 1 gateways...
Waiting for device binding...
Binding AHU-22 to GAT-123 (1/2)
Waiting 61s for 2 tasks to complete...
Binding SNS-4 to GAT-123 (2/2)
Finished binding gateways in 0.175
Updating site/devices/AHU-1/out/errors.map

Summary:
  Device Clean: 3
  Device Envelope: 1
  Device Validating: 1
Out of 4 total.
Registration summary available in /root/site/out/registration_summary.json
Registration summary available in /root/site/out/registration_summary.csv
```
