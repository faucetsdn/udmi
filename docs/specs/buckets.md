[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Buckets](#)

<!-- This file is consumed by the automatic generator script bin/gencode_buckets -->

# Buckets

Device feature buckets group multiple sequence tests together into logical
containers that can be used to describe the available device capabilities.
These are used to label individual line-item tests.

* _endpoint_: IoT connection endpoint management
  * _config_: Endpoint configuration updates
* _enumeration_: Basic device property enumeration capability
  * _pointset_: Enumerating available points of a device
  * _features_: Enumerating the features a device supports
  * _families_: Enumerating the network families of the device
* _discovery_: Automated discovery capabilities
  * _scan_: Scanning a network for devices
* _gateway_: UDMI gateway capabilities
* _pointset_: Pointset and telemetry capabilities
* _system_: Basic system operations
  * _mode_: System mode
* _writeback_: Writeback related operations
