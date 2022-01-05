[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./)
/ [Compliance](#)

# UDMI Compliance

This is an overview of what it means for a device to be "UDMI Compliant."
There are several different facets to UDMI, each providing a different
bit of functionality, so the actual requirements will depend on the
intended function of the device.

* [_pointset_ telemetry](../messages/pointset.md), used for basic telemetry ingestion.
* [_writeback_ control](./sequences/writeback.md), used for on-prem device control.
* [_gateway_ proxy](gateway.md), used to proxy data for non-MQTT devices.
* [_system_ basics](../messages/system.md), used for general monitoring and logging.

The [Tech Primer](../tech_primer.md) gives a primer for smart-ready building assembly requirements

## Compliance Matrix

The [compliance matrix](compliance_matrix.pdf) provides an overview of many of the functionality
defined within UDMI broken down into smaller individual feature sets. This aids in assessing levels
or completeness of compliance of a device and can be used for defining requirements.

[![Compliance Matrix](images/thumbnail_compliance_matrix.png)](compliance_matrix.pdf)

## Testing

The [validator](../tools/validator.md) and [sequencer](../tools/sequencer.md) tools can be used to
validate conformance to the schema.

The [registar](../tools/registrar.md#tool-execution) tool can be used to validate
[metadata](metadata.md) and [site models](site_model.md).

[DAQ](https://github.com/faucetsdn/daq) can be used to run some automated [UDMI
tests](https://github.com/faucetsdn/daq/blob/master/docs/cloud_tests.md) on devices