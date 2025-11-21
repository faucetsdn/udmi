### Sample registrar service output for bridgehead docker setup 

The registrar should only be run after the udmis service has been setup successfully (see the [sample udmis output](sample_outputs/udmis_output.md))

The registrar has been run successfully if you see no errors and can spot the lines: 


```
Processed AHU-22 (3/4) in 0.029s (add)
Processed SNS-4 (4/4) in 0.029s (add)
Processed AHU-1 (2/4) in 0.102s (add)
Processed GAT-123 (1/4) in 0.164s (add)
```

Sample output after running `sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto`:

```
starting run at 2025-11-05T10:12:02+00:00
java -cp /root/validator/build/libs/validator-1.0-SNAPSHOT-all.jar com.google.daq.mqtt.util.Dispatcher registrar site_model/ //mqtt/mosquitto
Writing reconciled configuration file to /root/out/registrar_conf.json
Using reflector iot client
Instantiating reflector client //mqtt/mosquitto/ ZZ-TRI-FECTA
10:12:03.559 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Loaded key /root/site_model/reflector/rsa_private.pkcs8 as sha256 42a8a8f1287aa775
10:12:03.569 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA token expiration sec 3600
10:12:03.585 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Using hash-key username/password /r/UDMI-REFLECT/d/ZZ-TRI-FECTA 42a8a8f1
10:12:03.686 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - CA cert file: /root/site_model/reflector/ca.crt
10:12:03.686 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Device cert file: /root/site_model/reflector/rsa_private.crt
10:12:03.686 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Private key file: /root/site_model/reflector/rsa_private.pem
10:12:03.686 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Password sha256 0049165a
10:12:03.703 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA creating client /r/UDMI-REFLECT/d/ZZ-TRI-FECTA on ssl://mosquitto:8883
10:12:03.917 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA creating new auth token for audience mosquitto
10:12:03.917 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Using hash-key username/password /r/UDMI-REFLECT/d/ZZ-TRI-FECTA 42a8a8f1
10:12:03.917 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA connecting to mqtt server ssl://mosquitto:8883
10:12:04.069 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 1 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/config
10:12:04.071 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 1 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/errors
10:12:04.072 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - Subscribing with qos 0 to topic /r/UDMI-REFLECT/d/ZZ-TRI-FECTA/commands/#
10:12:04.073 [main] INFO  c.g.bos.iot.core.proxy.MqttPublisher - ZZ-TRI-FECTA done with setup connection
Starting initial UDMI setup process
Sending UDMI reflector state to ZZ-TRI-FECTA: {
  "version" : "unknown",
  "udmi" : {
    "setup" : {
      "udmi_version" : "unknown",
      "functions_ver" : 18,
      "udmi_commit" : "unknown",
      "udmi_timever" : "unknown",
      "msg_source" : "debug",
      "tool_name" : "registrar",
      "transaction_id" : "RC:92f139.00000001"
    }
  },
  "timestamp" : "2025-11-05T10:12:03Z"
}
Received UDMI reflector initial config: {
  "last_state" : "2025-11-05T10:12:03Z",
  "reply" : {
    "udmi_version" : "unknown",
    "functions_ver" : 18,
    "udmi_commit" : "unknown",
    "udmi_timever" : "unknown",
    "msg_source" : "debug",
    "tool_name" : "registrar",
    "transaction_id" : "RC:92f139.00000001"
  },
  "setup" : {
    "hostname" : "5b24b7e56992",
    "functions_min" : 18,
    "functions_max" : 18,
    "udmi_version" : "1.5.4-21-g61e7dba5",
    "udmi_commit" : "61e7dba58",
    "udmi_ref" : "ghcr.io/faucetsdn/udmi:udmis-g61e7dba5",
    "udmi_timever" : "2025-11-04T13:20:19Z",
    "built_at" : "2025-11-04T13:30:00Z",
    "built_by" : "runner@runnervmf2e7y"
  }
}
Subscribed to /r/UDMI-REFLECT/d/ZZ-TRI-FECTA
Instantiated iot provider mqtt as IotReflectorClient
Working with project mosquitto registry us-central1/ZZ-TRI-FECTA
Loading site_defaults.json
Initializing 4 local devices...
Starting initialize settings for 4 devices...
Waiting 61s for 4 tasks to complete...
Fetching devices from registry ZZ-TRI-FECTA...
2025-11-05T10:12:04Z Fetched 0 devices.
Fetched 0 device models from cloud registry
Processing 4 new devices...
Waiting for device processing...
Waiting 61s for 4 tasks to complete...
Processed AHU-22 (3/4) in 0.029s (add)
Processed SNS-4 (4/4) in 0.029s (add)
Processed AHU-1 (2/4) in 0.102s (add)
Processed GAT-123 (1/4) in 0.164s (add)
Processed 4 (skipped 0) devices in 0.823s, 0.205s/d
Updating 0 existing devices...
Waiting for device processing...
Processed 0 (skipped 0) devices in 0.000s, 0.000s/d
Updated 0 device metadata files.
Finished processing 4/4 devices.
Binding devices to gateways: [GAT-123]
Waiting for device binding...
Waiting for tasks to complete...
Already bound to GAT-123: []
Binding [AHU-22, SNS-4] to GAT-123 (1/1)
Finished binding gateways in 0.151
Starting writing normalized for 4 devices...
Waiting 61s for 4 tasks to complete...
Writing normalized /root/site_model/devices/AHU-22/out/metadata_norm.json
Writing normalized /root/site_model/devices/GAT-123/out/metadata_norm.json
Writing normalized /root/site_model/devices/AHU-1/out/metadata_norm.json
Writing normalized /root/site_model/devices/SNS-4/out/metadata_norm.json
Starting previewing model for 4 devices...
Waiting 61s for 4 tasks to complete...
Starting validating expected for 4 devices...
Waiting 61s for 4 tasks to complete...
Starting validate samples for 4 devices...
Waiting 61s for 4 tasks to complete...
Starting validating keys for 4 devices...
Waiting 61s for 4 tasks to complete...
Starting process externals for 4 devices...
Waiting 61s for 4 tasks to complete...
Updating errors site_model/devices/AHU-1/out/errors.map

Summary:
  Device envelope: 1
  Device status: 4
  Device validation: 1
Out of 4 total.
Registration summary available in /root/site_model/out/registration_summary.json
Registration summary available in /root/site_model/out/registration_summary.csv
```