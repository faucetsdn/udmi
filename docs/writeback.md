# Writeback

This file documents UDMI's specification for cloud to device control i.e. writeback. At a high level, the cloud controls a device by writing to the [pointset block](/docs/pointset.md) in the device [config](/docs/config.md). After receiving the config, the device updates its state to reflect the status of the writeback attempt e.g. success, failure, etc.

## Cloud Behavior

To write to a point, the cloud sets two fields in the device config. First, `set_value`, which specifies the value for a given point in the device's current units. Second, `etag`, a value copied from the device's latest state message which uniquely represents the state. Specifically, `etag` is sent to avoid race conditions where the device's state changes in between the time the config message is sent by the cloud and the message is received by the device. 

## Device Behavior

After receiving the config message, the device attempts to write the requested value to the point. Depending on status of the write, the device should populate the `value_state` as described below.

### `value_state`

The `value_state` field is an enumeration representing the status of a point's writeback attempt.

Possible states for `value_state`:
*  \<missing\> -- The `value_state` field is missing from this point. This means one of two things:
      * The device is sending telemetry specified by the device as per normal operation. This is the more likely case.
      * The point’s config does not follow the UDMI spec for writeback. E.g. instead of specifying `set_value`, the config misspelled as ste_vlaue, which isn’t a valid field in UDMI, and is ignored by the device.
* applied -- The cloud value is applied to the device point. The point should now be reporting as telemetry the value defined in the device’s config.
* updating -- The system is working to make the device state match the requested config. If the system hasn’t reconciled the config within 5 seconds or before the next telemetry message (whichever comes first), then the system should send a state message with `value_state` set to updating.
While the system should never abort trying to reconcile the config, after 1 minute of attempting to reconcile, the system should set `value_state` to failure.
* overridden -- The device point has been superseded by another system/user with a higher priority. 
* invalid -- The system failed to write the value to the point because the requested value cannot be applied to the point. This state indicates an error on the cloud side. Some examples:
  * Point is not writable
  * Requested value is out of the operating bounds of the point
  * `etag` field in the config doesn't match the current state `etag`
failure -- The system failed to apply the cloud value to the point because an error occurred on the device side.

In the case of any of the error states (failure, invalid, overridden), the [status](/docs/status.md) field for the point should be populated to provide additional debugging information about the error.
