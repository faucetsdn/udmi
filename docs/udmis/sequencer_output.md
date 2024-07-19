[**UDMI**](../../) / [**Docs**](../) / [**UDMIS**](.) / [Sequencer Output](#)

Command for running sequencer in a docker container:
```
docker run --rm --net udminet --name sequencer -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/sequencer site/ //mqtt/udmis ${device_id} ${serial_no}
```

Sample output:
```
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
Using site model directory /root/site
Writing config to /tmp/sequencer_config.json:
{
  "iot_provider": "mqtt",
  "project_id": "udmis",
  "bridge_host": null,
  "udmi_namespace": null,
  "site_model": "/root/site",
  "device_id": "AHU-1",
  "alt_registry": null,
  "registry_suffix": null,
  "shard_count": null,
  "shard_index": null,
  "serial_no": "8127324",
  "log_level": "INFO",
  "min_stage": "PREVIEW",
  "udmi_version": "unknown",
  "udmi_root": "/root",
  "reflector_endpoint": null,
  "sequences": null,
  "key_file": "/root/site/reflector/rsa_private.pkcs8"
}
java -cp validator/build/libs/validator-1.0-SNAPSHOT-all.jar com.google.daq.mqtt.sequencer.SequenceRunner
Target sequence classes:
  com.google.daq.mqtt.sequencer.sequences.BlobsetSequences
  com.google.daq.mqtt.sequencer.sequences.ConfigSequences
  com.google.daq.mqtt.sequencer.sequences.DiscoverySequences
  com.google.daq.mqtt.sequencer.sequences.DiscoverySequences$1
  com.google.daq.mqtt.sequencer.sequences.GatewaySequences
  com.google.daq.mqtt.sequencer.sequences.LocalnetSequences
  com.google.daq.mqtt.sequencer.sequences.PointsetSequences
  com.google.daq.mqtt.sequencer.sequences.ProxiedSequences
  com.google.daq.mqtt.sequencer.sequences.SystemSequences
  com.google.daq.mqtt.sequencer.sequences.WritebackSequences
Reading config file /tmp/sequencer_config.json
Found target methods: endpoint_connection_retry, endpoint_connection_success_reconnect, endpoint_connection_success_alternate, endpoint_connection_error, endpoint_redirect_and_restart, endpoint_failure_and_restart
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_connection_retry
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_connection_success_reconnect
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_connection_success_alternate
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_connection_error
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_redirect_and_restart
Running target com.google.daq.mqtt.sequencer.sequences.BlobsetSequences#endpoint_failure_and_restart
Checking for modified metadata file /root/site/out/devices/AHU-1/metadata_mod.json
Reading device metadata file /root/site/devices/AHU-1/metadata.json
Writing results to /root/site/out/devices/AHU-1/RESULT.log
Loading reflector key file from /root/site/reflector/rsa_private.pkcs8
Validating against device AHU-1 serial 8127324
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
Setting state version unknown timestamp 2024-07-19T04:22:41Z
UDMI setting reflectorState: {
  "version" : "unknown",
  "udmi" : {
    "setup" : {
      "transaction_id" : "RC:8cc954.0001"
    }
  },
  "timestamp" : "2024-07-19T04:22:41Z"
}
UDMI received reflectorConfig: {
  "last_state" : "2024-07-19T04:22:41Z",
  "setup" : {
    "hostname" : "3d18e014852d",
    "functions_min" : 13,
    "functions_max" : 13,
    "udmi_version" : "1.4.2-248-gfeb3779c",
    "udmi_ref" : "ghcr.io/grafnu/udmi:udmis-gfeb3779cf",
    "built_at" : "2024-07-19T04:12:00Z",
    "built_by" : "testuser@testuser",
    "transaction_id" : "RC:8cc954.0001"
  }
}
UDMI matching against expected state timestamp 2024-07-19T04:22:41Z
UDMI version mismatch: unknown
UDMI functions support versions 13:13 (required 13)
No alternate registry configured, disabling
2024-07-19T04:22:41Z INFO Cleaning test output dir /root/site/out/devices/AHU-1/tests/endpoint_connection_retry
2024-07-19T04:22:41Z INFO Cleaning test output dir /root/site/out/devices/AHU-1/tests/endpoint_connection_retry
2024-07-19T04:22:41Z NOTICE starting test endpoint_connection_retry ################################
2024-07-19T04:22:41Z INFO Stage start waiting for starting test wrapper at 0s
2024-07-19T04:22:50Z INFO Stage start waiting for config sync at 9s
2024-07-19T04:22:50Z INFO Initial state #001: {
2024-07-19T04:22:50Z INFO Initial state #001:   "timestamp" : "2024-07-19T04:22:50Z",
2024-07-19T04:22:50Z INFO Initial state #001:   "version" : "1.5.1",
2024-07-19T04:22:50Z INFO Initial state #001:   "system" : {
2024-07-19T04:22:50Z INFO Initial state #001:     "last_config" : "2024-07-19T04:20:13Z",
2024-07-19T04:22:50Z INFO Initial state #001:     "operation" : {
2024-07-19T04:22:50Z INFO Initial state #001:       "operational" : true,
2024-07-19T04:22:50Z INFO Initial state #001:       "last_start" : "2024-07-19T04:21:03Z",
2024-07-19T04:22:50Z INFO Initial state #001:       "restart_count" : 1,
2024-07-19T04:22:50Z INFO Initial state #001:       "mode" : "initial"
2024-07-19T04:22:50Z INFO Initial state #001:     },
2024-07-19T04:22:50Z INFO Initial state #001:     "serial_no" : "8127324",
2024-07-19T04:22:50Z INFO Initial state #001:     "hardware" : {
2024-07-19T04:22:50Z INFO Initial state #001:       "make" : "BOS",
2024-07-19T04:22:50Z INFO Initial state #001:       "model" : "pubber"
2024-07-19T04:22:50Z INFO Initial state #001:     },
2024-07-19T04:22:50Z INFO Initial state #001:     "software" : {
2024-07-19T04:22:50Z INFO Initial state #001:       "firmware" : "v1"
2024-07-19T04:22:50Z INFO Initial state #001:     },
2024-07-19T04:22:50Z INFO Initial state #001:     "status" : {
2024-07-19T04:22:50Z INFO Initial state #001:       "message" : "success",
2024-07-19T04:22:50Z INFO Initial state #001:       "category" : "system.config.parse",
2024-07-19T04:22:50Z INFO Initial state #001:       "timestamp" : "2024-07-19T04:22:50Z",
2024-07-19T04:22:50Z INFO Initial state #001:       "level" : 100
2024-07-19T04:22:50Z INFO Initial state #001:     }
2024-07-19T04:22:50Z INFO Initial state #001:   },
2024-07-19T04:22:50Z INFO Initial state #001:   "localnet" : {
2024-07-19T04:22:50Z INFO Initial state #001:     "families" : {
2024-07-19T04:22:50Z INFO Initial state #001:       "vendor" : {
2024-07-19T04:22:50Z INFO Initial state #001:         "addr" : "28179023"
2024-07-19T04:22:50Z INFO Initial state #001:       },
2024-07-19T04:22:50Z INFO Initial state #001:       "ether" : {
2024-07-19T04:22:50Z INFO Initial state #001:         "addr" : "02:42:c0:a8:63:03"
2024-07-19T04:22:50Z INFO Initial state #001:       },
2024-07-19T04:22:50Z INFO Initial state #001:       "ipv4" : {
2024-07-19T04:22:50Z INFO Initial state #001:         "addr" : "192.168.99.3"
```

...this will go on for quite some time, until ultimately it should end with something like:
```
Missing reference file: validator/sequences/pointset_publish/sequence.md
Missing reference file: validator/sequences/pointset_publish_interval/sequence.md
Missing reference file: validator/sequences/pointset_remove_point/sequence.md
Missing reference file: validator/sequences/pointset_request_extraneous/sequence.md
Missing reference file: validator/sequences/broken_config/sequence.md
Missing reference file: validator/sequences/config_logging/sequence.md
Missing reference file: validator/sequences/device_config_acked/sequence.md
Missing reference file: validator/sequences/extra_config/sequence.md
Missing reference file: validator/sequences/family_ether_addr/sequence.md
Missing reference file: validator/sequences/family_ipv4_addr/sequence.md
Missing reference file: validator/sequences/family_ipv6_addr/sequence.md
Missing reference file: validator/sequences/state_make_model/sequence.md
Missing reference file: validator/sequences/state_software/sequence.md
Missing reference file: validator/sequences/system_last_update/sequence.md
Missing reference file: validator/sequences/valid_serial_no/sequence.md
Report saved to: /root/site/out/devices/AHU-1/results.md
```
