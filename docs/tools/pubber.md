[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Pubber](#)

# Pubber Reference Client

The _Pubber_ reference client is a sample implementation of a client-side
'device' that implements the UDMI schema. It is used to simulate  devices
registered in the [UDMI site model](../specs/site_model.md). 

It's not intended to be any sort of production-worthy code or library, rather
just a proof-of-concept of what needs to happen.

## Running Pubber

Pubber is run from the CLI within the UDMI directory.

`bin/pubber SITE_PATH PROJECT_ID DEVICE_ID SERIAL_NO [options]`

* `SITE_PATH` - path to site model
* `PROJECT_ID` - GCP project ID
* `DEVICE_ID` - device ID to simulate (the device must exist in the site model
  and be registered)
* `SERIAL_NO` - serial number to use (can be any alphanumeric string)
* `[options]` - optional configuration parameters which change pubber behavior

### Options

The following parameters are currently supported from the CLI:
* `extraPoint=<name>` - adds an extra point with the given name to the device
  which does not exist in device's metadata with a random value (will trigger
  validation additional point error)
* `missingPoint=<name>` - removes the point with the given name (if exists) from
  the device's active pointset at initialization  (will trigger validation
  missing point)
* `extraField=<name>` - adds an extra schema invalidating field to pointset events
  (will trigger validation schema error)
* `noHardware` - omits the `system.hardware` field from state messages (will
  trigger validation error, missing required field)
* `noConfigAck` - subscribes to the `config` topic with a QoS of 0, therefore
  will not send PUBACK's for config messages


More advanced options can be set by by calling pubber directly with the path a
configuration file: `pubber/bin/run path/to/config.json`

## Operation

```
user@machine:~/udmi$ bin/pubber sites/udmi_site_model project_id AHU-1 123
Building pubber...
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Running tools version 1.3.8-242-g9652916
INFO daq.pubber.Pubber - 2022-05-24T15:26:19Z Configuring with key type RS256
INFO daq.pubber.Pubber - 2022-05-24T15:26:19Z Starting pubber AHU-1, serial 123, mac null, extra null, gateway null
INFO daq.pubber.Pubber - 2022-05-24T15:26:19Z Loading device key bytes from sites/udmi_site_model/devices/AHU-1/rsa_private.pkcs8
INFO daq.pubber.Pubber - 2022-05-24T15:26:19Z update state 2022-05-24T15:26:19Z last_config null
INFO daq.pubber.MqttPublisher - Creating new mqtt client for projects/project_id/locations/us-central1/registries/ZZ-TRI-FECTA/devices/AHU-1
INFO daq.pubber.Pubber - 2022-05-24T15:26:19Z State update:
{
  "timestamp" : "2022-05-24T15:26:19Z",
  "system" : {
    "operational" : true,
    "serial_no" : "123",
    "hardware" : {
      "make" : "BOS",
      "model" : "pubber"
    },
    "software" : {
      "firmware" : "v1"
    }
  },
  "pointset" : {
    "points" : {
      "filter_alarm_pressure_status" : { },
      "filter_differential_pressure_setpoint" : { },
      "filter_differential_pressure_sensor" : { }
    }
  }
}
INFO daq.pubber.MqttPublisher - Attempting connection to projects/project_id/locations/us-central1/registries/ZZ-TRI-FECTA/devices/AHU-1
INFO daq.pubber.MqttPublisher - Password hash 38269d117e7d818bd1cb47274e6eaf1a788cf36f96a83430e595d2e560e570f9
INFO daq.pubber.MqttPublisher - Updates subscribed
INFO daq.pubber.Pubber - 2022-05-24T15:26:21Z Connection complete.
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z system.config.receive success
WARN daq.pubber.Pubber - 2022-05-24T15:26:22Z defer state update 1866
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z system.config.parse success
WARN daq.pubber.Pubber - 2022-05-24T15:26:22Z defer state update 1822
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z Config handler
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z New config:
{
  "timestamp" : "2022-05-10T15:43:37Z",
  "version" : "1.3.14",
  "pointset" : {
    "points" : {
      "filter_alarm_pressure_status" : {
        "ref" : "BV11.present_value"
      },
      "filter_differential_pressure_setpoint" : {
        "set_value" : 98
      },
      "filter_differential_pressure_sensor" : {
        "ref" : "AV12.present_value"
      }
    }
  }
}
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z 2022-05-24T15:26:22Z received config 2022-05-10T15:43:37Z
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z Starting executor with send message delay 10000
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z system.config.apply success
INFO daq.pubber.Pubber - 2022-05-24T15:26:22Z synchronized start config result true
INFO daq.pubber.Pubber - Done with main
WARN daq.pubber.Pubber - 2022-05-24T15:26:22Z defer state update 1792
WARN daq.pubber.Pubber - 2022-05-24T15:26:22Z defer state update 1787
INFO daq.pubber.Pubber - 2022-05-24T15:26:23Z update state 2022-05-24T15:26:23Z last_config 2022-05-10T15:43:37Z
INFO daq.pubber.Pubber - 2022-05-24T15:26:23Z State update:
{
  "timestamp" : "2022-05-24T15:26:23Z",
  "version" : "1.3.14",
  "system" : {
    "last_config" : "2022-05-10T15:43:37Z",
    "operational" : true,
    "serial_no" : "123",
    "hardware" : {
      "make" : "BOS",
      "model" : "pubber"
    },
    "software" : {
      "firmware" : "v1"
    }
  },
  "pointset" : {
    "points" : {
      "filter_alarm_pressure_status" : { },
      "filter_differential_pressure_setpoint" : {
        "value_state" : "applied"
      },
      "filter_differential_pressure_sensor" : { }
    }
  }
}
INFO daq.pubber.Pubber - 2022-05-24T15:26:32Z 2022-05-24T15:26:32Z sending test message #0
```


## Cloud Setup

To use Pubber, there needs to be a cloud-side device entry configured in a GCP project configured to
use [Cloud IoT](https://cloud.google.com/iot/docs/). The
[Creating or Editing a Device](https://cloud.google.com/iot/docs/how-tos/devices#creating_or_editing_a_device)
section of the documentation describe how to create a simple device and key-pair (see next section for
a helper script). You can/should substitute the relevant values in the configuration below for your
specific setup. The relevant bits of configuration are the information in the <code>local/pubber.json</code>
file (see above), and the generated public key (also see above).

Alternatively, you can use the [registrar tool](registrar.md) to automate device registration.
