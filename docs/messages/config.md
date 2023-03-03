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

This [working example](../../tests/config.tests/example.json) shows how a typical `config` message
is constructed.

## Config Parameters

### System
* `min_loglevel`: Indicates the minimum loglevel for reporting log messages below which log entries
should not be sent. See note below for a description of the level value.

### Pointset

* `sample_rate_sec`: Sampling rate for the system, which should proactively send an
update (e.g. _pointset_, _logentry_, _discover_ message) at this interval.
* `sample_limit_sec`: Minimum time between sample updates. Updates that happen faster than this time
(e.g. due to `cov` events) should be coalesced so that only the most recent update is sent.

#### Point

* `set_value`: Set a value to be used during diagnostics and operational use. Should
override any operational values, but not override alarm conditions.

## Config Log Entries

During a config receive/up sequence, the system should produce the following log/status update entries:
* _system.config.receive_: (**DEBUG**) Receiving a config message
* _system.config.parse_: (**DEBUG**) Parsing a received message
* _system.config.apply_: (**NOTICE**) Application of a parsed config message

