[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Device Testing with Sequencer](#)

# Device Testing with Sequencer 

## Overview

The [sequencer](../tools/sequencer.md) tool runs automated testing against the device to determine compliance against parts of the UDMI specification

For complete device testing, sequencer can be used, however there are additional steps which need to be taken to ensure a successful test session with accurate results.

The device must support at a minimum the [basic state/config transaction](../specs/sequences/config.md). The device should be configured so that it is free of error, configured with at least a couple of points and be actively and frequently publishing data. The devices [`metadata.json`](../specs/site_model.md) file must contain the correct information for the device as configured, as well as include additional parameters which are exclusively used by sequencer during sequence testing. This is described in greater detail in the below headings.

## Requirements

#### Device's UDMI Implementation Prerequisites 

The device must at a minimum support the [basic state/config transaction](../specs/sequences/config.md)

This comprises:

- Subscribe to the configuration topic (`/devices/{DEVICE_ID}/config`}
- Upon receiving a **config** message, publish a **state** message to the state topic (`/devices/{DEVICE_ID/state`}, in which
  - The `system.last_config` property in the state message matches the `timestamp` property within the config message which was published

This is because the [sequencer](../tools/sequencer.md) tool will reset the configuration before each test runs, and ensure that the device is synchronized with the sequencer.

**IF** a device does not support this, [sequencer](../tools/sequencer.md) can still be used, however only a very limited subset of tests which do not require state messages can be run. This setting the `testing.noState` property to `true` in the devices metadata.json file, for example:

```json
"testing": {
	"nostate":	true
}
```

#### Device Configuration Requirements 

- The device must be online and actively publishing data
  - Recommended to set initial telemetry publish intervals to 20s so that the sequencer testing takes a reasonable amount of time and the correct results are produced.
- The device must be fully and properly configured.
  - If the device is a gateway device, then a proxy device must be configured which must also meet all the requirements
- The device must include at least two points, one of which is writeable
- Under normal operating conditions, the device must not be reporting any status with a level of warning (400) or above in the state message
  - If there is, sequencer will fail all tests.

#### Sequencer/Metadata Configuration Requirements 

The device's metadata.json file must accurately describe the device. In some tests, the sequencer will compare the data in metadata with the device that the data is publishing. Incorrect or missing data will result in the failure or skipping of tests.

- Points must match (`pointset.points`) what the device is publishing, and include any required properties to describe the device (e.g. `ref` fields)
- The devices `system.hardware.make` and `system.hardware.model` must be correct and match what the device is publishing (e.g., if a device does include this property in its state message, and it reads `Google LLC`, the property must be the same. Just `Google` will prompt failure of these tests.
- Similarly, the devices `system.software` must be a subset of the properties a device is publishing in its state message (if it does). If a device publishes multiple properties, e.g. `os`, `firmware`, and `build` - it is acceptable to only include one of these in the metadata, e.g. `firmware`.
- The IPv4 (if applicable), IPv6 (if applicable) and MAC address are included in the localnet.families.ipv4, localnet.families.ipv4 and localnet.families.ether properties in the metadata

For example:

```json
// rest of metadata file
 "system": {
    "hardware": {
      "make": "BOS",
      "model": "pubber"
    },
    "software": {
      "firmware": "v1"
    },
  },
  "localnet": {
    "families": {
      "ipv4": {
        "addr": "192.168.2.1"
      },
      "ether": {
        "addr": "00:50:b6:ed:5f:77"
      }
    }
  }
```

Some tests use parameters defined in the metadata.json file. Presently, these are only for writeback testing. If a parameter is not defined, then the respective test is skipped. These are:

- `testing.targets.applied` - the `target_point` value is the name of a point which can successfully be written to, and the `target_value` is the value which can be successfully applied
- `testing.targets.failure` - the `target_point` and `target_value` is the name of a point and the respective value which results in a `value_state` of `failure` when writeback is attempted
- `testing.targets.invalid` - the `target_point` and `target_value` is the name of a point and the respective value which results in a `value_state` of `invalid` when writeback is attempted

For example,

```json
    // rest of metadata file
  "testing": {
    "targets": {
      "applied": {
        "target_point": "filter_differential_pressure_setpoint",
        "target_value": 60
      },
      "failure": {
        "target_point": "filter_alarm_pressure_status",
        "target_value": false
      },
      "invalid": {
        "target_point": "filter_differential_pressure_sensor",
        "target_value": 15
      }
    }
```

For endpoint redirection testing, the `alt_registry` property in the `cloud_iot_config.json` file must be set and correspond to a valid registry in which the device is registered in. Run registrar within that registry (by changing the `registry_id` property temporarily) to ensure the device is registered.

## Testing Process

Before running the [sequencer](../tools/sequencer.md) for the first time, it is recommended to perform a few checks to ensure the device is correctly operating and ready for sequence testing. Otherwise, the sequencer will likely take a long time to run, and all tests will fail. This also mitigates against failures from site model errors, or stale site model files.

### 1. Run Registrar

Run [registrar](../tools/registrar.md) against the device to be tested to validate metadata files, and update the device configuration.

### 2. Run Validator

Run [validator](../tools/validator.md) and check that the:

  - The device is functional online
  - The device is publishing data
  - The state message does not contain a status

### 3. Run Sequencer

[sequencer](../tools/sequencer.md) can be launched using the command:

```
bin/sequencer PATH_TO_SITE_MODEL PROJECT_ID DEVICE_ID [SERIAL]
```

[sequencer](../tools/sequencer.md) can take between ten minutes to an hour + to complete all the tests, depending on what functionalities a device supports.. This is because each test has a time limit. If a device supports a given functionality, it will pass the test, otherwise the sequencer waits until the time limit expires.

The [sequencer](../tools/sequencer.md) documentation provides additional run time options and settings.

When the sequencer is finished, a report is generated in the `out/devices/DEVICE_ID` directory. Complete test results, with logs, are in the `tests` sub-directory,

When tests fail, the report includes the failure reason, and an indication of where in the sequence the device failed.

## Tips and Common Failures

### Test failed: Timeout waiting for config sync 

Synchronization between sequencer and the device has failed. This typically occurs when:

- The device is offline
- The device does not support the state/config transaction, either:
  - Does not publish state
  - `last_config` does not match the `timestamp` of the last config sent

### Test failed: Timeout waiting for no applicable system status

This typically occurs when the device has a `status` in the `system` block of the state message. Sequencer requires that a device has no `status` before it commences testing. Refer to the details in the status message for additional information. This can typically be caused by:

- Device uses a template which includes a static status
- The UDMI configuration is invalid for the device and should be correct
- The local configuration of the device has something wrong in its configuration or another error
