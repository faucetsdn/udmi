# Config block

(This is a somewhat temporary placeholder until better documentation can be written.)

The UDMI config block species the
[Cloud IoT Core Config](https://cloud.google.com/iot/docs/how-tos/config/configuring-devices)
block that controlls a device's intended behavior.

It is composed of specific sub-entries for each sub-system { _system_, _pointset_, _gateway_, etc... }.

This [working example](../tests/config.tests/example.json) shows how a typical `config` message
is constructed.

## Config Message

* `sample_rate_sec`: Sampling rate for the system, which should proactively send an
update (e.g. _pointset_, _logentry_, _discover_ message) at this interval.
* `sample_limit_sec`: Minimum time between sample updates. Updates that happen faster than this time
(e.g. due to _cov_ events) should be coalesced so that only the most recent update is sent.
* `set_value`: Set a value to be used during diagnostics and operational use. Should
override any operational values, but not override alarm conditions.
* `min_loglevel`: Indicates the minimum loglevel for reporting log messages below which log entries
should not be sent. See note below for a description of the level value.
