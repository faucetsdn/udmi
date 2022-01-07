[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Config](#)

# Config

**Schema Definition:** [config.json](../../schema/config.json)
 ([_🧬View_](../../gencode/docs/config.html))

The UDMI config block specifies the
[Cloud IoT Core Config](https://cloud.google.com/iot/docs/how-tos/config/configuring-devices)
block that controls a device's intended behavior.

Unless a config message has an [expiry](../specs/sequences/writeback.md#value-expiration), the latest
config message is always considered present.

It is composed of specific sub-entries for each sub-system { _system_, _pointset_, _gateway_, etc... }.

An example `config` message is below 

```json
{
  "version": 1,
  "timestamp": "2018-08-26T21:39:29.364Z",
  "system": {
    "min_loglevel": 500
  },
  "pointset": {
    "sample_limit_sec": 2,
    "sample_rate_sec": 500,
    "points": {
      "return_air_temperature_sensor": {
      },
      "nexus_sensor": {
        "ref": "ziuewwedf"
      }
    }
  }
}
```

## Config Message

* `sample_rate_sec`: Sampling rate for the system, which should proactively send an
update (e.g. _pointset_, _logentry_, _discover_ message) at this interval.
* `sample_limit_sec`: Minimum time between sample updates. Updates that happen faster than this time
(e.g. due to _cov_ events) should be coalesced so that only the most recent update is sent.
* `set_value`: Set a value to be used during diagnostics and operational use. Should
override any operational values, but not override alarm conditions.
* `min_loglevel`: Indicates the minimum loglevel for reporting log messages below which log entries
should not be sent. See note below for a description of the level value.
