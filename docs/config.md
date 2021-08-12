# Config block

(This is a somewhat temporary placeholder until better documentation can be written.)

The UDMI config block species the
[Cloud IoT Core Config](https://cloud.google.com/iot/docs/how-tos/config/configuring-devices)
block that controlls a device's intended behavior.

It is composed of specific sub-entries for each sub-system { _system_, _pointset_, _gateway_, etc... }.

This [working example](../tests/config.tests/example.json) shows how a typical `config` message
is constructed.
