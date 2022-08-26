[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Onboarding](#)

# Validation

_Streaming validation_ is a process where an agent receives messages from a device,
process them for UDMI compliance, and then send a _validation result_ message with the
results. Additionally, the system periodically sends a _validation report_ message
that summarizes the state of discovery for that site (e.g. which devices have been
seen, or never seen, etc...). This capability is an extension of the basic
[validator](../tools/validator.md) capability.

## Message Types

The validation agent runs autonomously, consumes messages, and injects _result_ or _report_ messages as needed.
All validation messages are defined by the
[validation event schema](../../schema/event_validation.json)([_🧬View_](../../gencode/docs/event_validation.html)).
and
[validation state schema](../../schema/state_validation.json)([_🧬View_](../../gencode/docs/state_validation.html)).
schema, and are instantiated in two flavors:

* _result_: Validation results for an individual device [example](../../tests/event_validation.tests/simple_ok.json).
* _report_: Validation report for an entire site [example](../../tests/state_validation.tests/report.json).

## Message Channel

The message validator will automatically use the inverse channel it uses for receiving messages. So, if it
received a message over PubSub, it will send one back accordingly. If it receives them over the Reflector, then
it uses the reflector to send them back.

Messages sent from the validator will have the `deviceId` set to the ID of the device that was validated. In the
case of a site-wide report, the `deviceId` will be set to `_validator`. Other message attributes should be the
same as if the message were coming from the device itself (project, registry, etc...).

### Reflector Setup

The reflector uses the same reflector channel as receiving messages, so no additional setup is required.

### PubSub Setup

* Make sure the system is working as per the basic [validator](../tools/validator.md) setup.
* Add an `update_topic` field to the site's `cloud_iot_config.json` file, indicating the PubSub topic for outgoing messages (e.g. `udmi_reflect`).

If this all worked, you should see a message like then on startup:
```
Sending validation updates to projects/bos-udmi-dev/topics/udmi_reflect
```

## Verifying Output

If everything worked as intended, there should be periodic validation messages that show up on the `udmi_target` topic:

<pre>
  attribute.subFolder = 'validation'
  attribute.subType = 'state' <i>or</i> '_validator'
  attribute.deviceId = '_validator' or <i>deviceId of validated device</i>
</pre>
