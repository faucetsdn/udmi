[**UDMI**](../../) / [**Docs**](../) / [**UDMIS**](.) / [Pubber Output](#)

Sample command to run pubber in a docker container:
```
docker run -d --rm --net udminet --name pubber -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:pubber-latest bin/pubber site/ //mqtt/udmis ${device_id} ${serial_no}
```

`docker logs pubber` sample output:
```
Unable to find image 'ghcr.io/faucetsdn/udmi:pubber-latest' locally
pubber-latest: Pulling from faucetsdn/udmi
ec99f8b99825: Already exists 
efbe3ea3836c: Pull complete 
f22a8d45822e: Pull complete 
d1b89b9bf41b: Pull complete 
170b8f92ca0f: Pull complete 
25c526f5b443: Pull complete 
Digest: sha256:91f4637277af1133074ba200f7ade2437cd21d060ad60544175ca5d9bc0af806
Status: Downloaded newer image for ghcr.io/faucetsdn/udmi:pubber-latest
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
Constructing pubber config from command line args.
Cleaning output directory /root/pubber/out/8127324
Building pubber...
Running tools version unknown
Attached to gateway null
Target is AHU-1
Extracting hashed password from key file /root/site/devices/AHU-1/rsa_private.pkcs8
Checking for signed device certificate...
Generating CERT with altname udmis
Generating self-signed cert from CA defined in /root/site/reflector
Generating cert for device keys in /root/site/devices/AHU-1
Certificate request self-signature ok
subject=CN=client
Done with keygen.
-rw-r--r--    1 root     root          1123 Jul 19 04:21 /root/site/devices/AHU-1/rsa_private.crt
Original noPersist is null
java -Dorg.slf4j.simpleLogger.showThreadName=false -jar /root/pubber/build/libs/pubber-1.0-SNAPSHOT-all.jar /tmp/pubber_config.json
Waiting for pubber pid 38 to complete...
INFO daq.pubber.Pubber - Device start time is 2024-07-19T04:21:03Z
INFO daq.pubber.Pubber - State update defer -1721362861116ms
INFO daq.pubber.Pubber - State update defer -1721362861117ms
INFO daq.pubber.Pubber - Using addresses from default interface eth0
INFO daq.pubber.Pubber - Family ipv4 address is 192.168.99.3
INFO daq.pubber.Pubber - Family ether address is 02:42:c0:a8:63:03
INFO daq.pubber.Pubber - State update defer -1721362861137ms
INFO daq.pubber.Pubber - State update defer -1721362861137ms
INFO daq.pubber.Pubber - Using addresses from default interface eth0
INFO daq.pubber.Pubber - Family ipv4 address is 192.168.99.3
INFO daq.pubber.Pubber - Family ether address is 02:42:c0:a8:63:03
INFO daq.pubber.Pubber - State update defer -1721362861139ms
INFO daq.pubber.Pubber - State update defer -1721362861139ms
INFO daq.pubber.Pubber - Using addresses from default interface eth0
INFO daq.pubber.Pubber - Family ipv4 address is 192.168.99.3
INFO daq.pubber.Pubber - Family ether address is 02:42:c0:a8:63:03
INFO daq.pubber.Pubber - State update defer -1721362861140ms
INFO daq.pubber.Pubber - State update defer -1721362861140ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Writing pubber feature file to /root/site/out/pubber_features.json
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Configured with auth_type RS256
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861212ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Resetting persistent store /root/site/out/devices/AHU-1/persistent_data.json
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Loading endpoint into persistent data from configuration
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Updating persistent store:
{
  "endpoint" : {
    "protocol" : "mqtt",
    "transport" : "ssl",
    "hostname" : "udmis",
    "client_id" : "/r/ZZ-TRI-FECTA/d/AHU-1",
    "topic_prefix" : "/r/ZZ-TRI-FECTA/d/AHU-1",
    "auth_provider" : {
      "basic" : {
        "username" : "/r/ZZ-TRI-FECTA/d/AHU-1",
        "password" : "38269d11"
      }
    }
  },
  "restart_count" : 1
}
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Starting pubber AHU-1, serial 8127324, mac null, gateway null, options noPersist
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861218ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Loading device key bytes from /root/site/devices/AHU-1/rsa_private.pkcs8
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z CA cert file: /root/site/reflector/ca.crt
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Device cert file: /root/site/devices/AHU-1/rsa_private.crt
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Private key file: /root/site/devices/AHU-1/rsa_private.pem
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Password sha256 6b5344e1
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Publishing dirty state block
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Starting connection 1721362863295 with 10
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861295ms
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Creating new config latch for AHU-1
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Update state 2024-07-19T04:21:03Z last_config 1970-01-01T00:00:00Z
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Dropping state update until config received...
INFO daq.pubber.MqttPublisher - Creating new client to ssl://udmis:8883 as /r/ZZ-TRI-FECTA/d/AHU-1
INFO daq.pubber.MqttPublisher - Auth using username /r/ZZ-TRI-FECTA/d/AHU-1
INFO daq.pubber.MqttPublisher - Attempting connection to /r/ZZ-TRI-FECTA/d/AHU-1
INFO daq.pubber.MqttPublisher - Subscribed to mqtt topic /r/ZZ-TRI-FECTA/d/AHU-1/config (qos 1)
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Log DEBUG* system.config.receive success 2024-07-19T04:21:03Z
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861885ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Update state 2024-07-19T04:21:03Z last_config 1970-01-01T00:00:00Z
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Dropping state update until config received...
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Log DEBUG* system.config.parse success 2024-07-19T04:21:03Z
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861916ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Device AHU-1 config handler
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Update state 2024-07-19T04:21:03Z last_config 1970-01-01T00:00:00Z
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Dropping state update until config received...
INFO daq.pubber.MqttPublisher - Subscribed to mqtt topic /r/ZZ-TRI-FECTA/d/AHU-1/errors (qos 0)
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Connection complete.
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Start waiting 10s for config latch for AHU-1
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Config update AHU-1:
{
  "timestamp" : "2024-07-19T04:20:13Z",
  "version" : "1.4.2-248-gfeb3779c",
  "system" : {
    "min_loglevel" : 300,
    "metrics_rate_sec" : 10,
    "operation" : { }
  },
  "localnet" : {
    "families" : {
      "ipv4" : { },
      "vendor" : { },
      "ether" : { }
    }
  },
  "pointset" : {
    "points" : {
      "filter_alarm_pressure_status" : {
        "ref" : "BV11.present_value",
        "units" : "No-units"
      },
      "filter_differential_pressure_setpoint" : {
        "units" : "Bars",
        "set_value" : 98
      },
      "filter_differential_pressure_sensor" : {
        "ref" : "AV12.present_value",
        "units" : "Degrees-Celsius"
      }
    }
  }
}
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z 2024-07-19T04:21:03Z received config 2024-07-19T04:20:13Z
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Starting AHU-1 PointsetManager sender with delay 10s
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Restoring unknown point filter_alarm_pressure_status
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Restoring unknown point filter_differential_pressure_setpoint
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Restoring unknown point filter_differential_pressure_sensor
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Starting AHU-1 SystemManager sender with delay 10s
INFO daq.pubber.MqttPublisher - Sending message to /r/ZZ-TRI-FECTA/d/AHU-1/events/system
WARN daq.pubber.Pubber - 2024-07-19T04:21:03Z Starting AHU-1 Pubber sender with delay 10s
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z State update defer -1721362861989ms
INFO daq.pubber.Pubber - 2024-07-19T04:21:03Z Update state 2024-07-19T04:21:03Z last_config 2024-07-19T04:20:13Z
```
_...output continues indefinitely..._
