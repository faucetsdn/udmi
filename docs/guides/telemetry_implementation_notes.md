[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Telemetry Implementation Notes](#)

# Telemetry Implementation Notes

## Overview
UDMI offers a lot of flexibility for the messages associated with telemetry. There are some aspects of telemetry concepts in general that might warrant additional clarification on projects in order to provide a consistent implementation across installations.

## Units of measure
UDMI [stipulates](..\messages\pointset.md#metadata) that units of measure must be provided if the telemetry value is numeric, but does not prescribe what system to use to represent them. There are a number of systems that could be considered that offer unambiguous fixed numeric or fixed string codes for units of measure.

### UCUM
The Unified Code for Units of Measure (UCUM) is a system of codes for unambiguously representing units. Its focus is on machine-to-machine communication, and various tools exist to aid with validation and conversion.

### BACnet
The BACnetEngineeringUnits enumeration from the BACnet protocol specification provides a standardised numeric identifier for the unit of measure associated with a BACnet property value, ensuring that BACnet devices can unambiguously interpret engineering units without relying on free-text strings.

### DBO
The Digital Building Ontology project provides a list of units of measure as strings.

### QUDT
QUDT (Quantities, Units, Dimensions, and Data Types) is an ontology that provides a standardised, machine-readable framework to represent physical quantities, their units and dimensions, enabling unambiguous interpretation and conversion.

## Single pointset configuration
A core design philosophy of UDMI is that at any one time, one device has one current *configuration*. This configuration [specifies](..\messages\pointset.md#config) a number of aspects that relate to telemetry, such as which points are included within the pointset sent by the device.

If a target system requires just 1 point from a device, and a separate target system requires 10 points from the same device, the configuration will need to contain all 11 points. That is, both target systems will receive all 11 points.

Further, if the former target system requires the 1 point to be sent every minute, and the latter target system requires all 10 points to be sent once every 24 hours, then both target systems will receive all 11 points every minute.

## Serialisation of boolean and enumerated points
UDMI [stipulates](..\messages\pointset.md#event) that numeric values should be serialised as a JSON [number](https://datatracker.ietf.org/doc/html/rfc8259#section-6), and the associated units should be provided separately in [metadata](..\messages\pointset.md#metadata). That is, numeric values are sent on the wire as numbers only in the events/pointset message. It is up to the target systems to apply or suffix the meaning (the units of measure) when displaying the number.

A similar stipulation is not explicitly made for boolean values, although it could be encouraged to serialise boolean values using the `true` and `false` JSON [literals](https://datatracker.ietf.org/doc/html/rfc8259#section-3). This may appear to work well for boolean points such as 'pump run status' (where it could be presumed that `true` means 'pump running'), but the interpretation could be less clear for boolean points such as 'door open status' (where it may not be clear if `true` means 'open' or 'not open'). UDMI does not have a dedicated construct to store the fixed meanings of `true` and `false` within [metadata](..\messages\pointset.md#metadata) currently, and so it would be up to the target systems to capture and apply the meaning when displaying the boolean value.

Likewise there is no stipulation made for enumerated, or 'multi-state', values which are commonplace in smart building systems. Communication protocols such as BACnet, KNX, DALI and OPC all have standardised notions of enumerated, or 'multi-state' points, and they all transmit their values on the wire by using fixed integers to represent the ordinal. These fixed integers all have fixed meanings. UDMI does not have a dedicated construct to store the fixed meanings of ordinals within [metadata](..\messages\pointset.md#metadata) currently, and there is no concept of an enum data type in JSON. At present, UDMI is unopinionated on whether the integer value is sent, or a string representation of the fixed meaning.

For both boolean and enumerated values, the decision as to whether to send the values in their source form (typically `true`, `false`, or the ordinal), or whether to send their fixed meaning as a string, should be influenced by the capabilities of the target systems and their associated ETL pipelines. The decision may also be influenced by the capabilities of any [writeback](..\specs\sequences\writeback.md) mechanism that the systems may implement.

## Configuring change of value updates for boolean or string points
UDMI has a [construct](..\messages\pointset.md#config) to control the tolerance for change of value updates for numeric points, but there is no construct to selectively enable or disable change of value updates for boolean or string points.

## Point status is conveyed in a separate message to point value
The *status* of a point can be used to give additional context to the *value* of a point. For example, a temperature sensor point could have a value of `-3276`, and an accompanying status for the point could provide the context that the point is deemed to be in 'fault', and therefore the value should perhaps be considered invalid.

In UDMI the *status* of a point is conveyed in a separate message to the *value* of the point, which could arrive at separate times. This is different to other protocols such as BACnet where the *status* of a point is conveyed at the same time within the same 'object' as the *value*. In UDMI the status of a point is sent in the `/state` message, whereas the value of a point is sent in the `/events/pointset` message.

This is worth being cognisant of for target systems that make decisions based on the *value* of points. If a `/state` message isn't received before an associated `/events/pointset` message, the target system could make decisions based on a potentially invalid *value*.

UDMI does not prescribe the order of messages in this scenario, but it could be considered beneficial to ensure that `/state` messages are sent prior to any corresponding `/events/pointset` messages, so that the target system can correctly contextualise upcoming *values*.

## MQTT topic structure
UDMI [stipulates](..\specs\tech_stack.md#mqtt-topic-suffix-table) the topic suffixes that devices should use when publishing messages. It does not stipulate what the preceding part of the topic should be.

Some MQTT implementations, such as ClearBlade IoT Core's MQTT bridge, [stipulate](https://docs.clearblade.com/iotcore/publishing-over-mqtt#PublishingoverMQTT-Publishingtelemetryevents) what the complete topic structure should be. In the case of ClearBlade IoT Core, for an /events/pointset message from device FCU-1 it would mandate the topic structure below.

```
/devices/FCU-1/events/pointset
```

Using this topic structure may not be possible if an installation is using a regular MQTT broker that is shared across multiple sites. In this case it may warrant introducing a site code at the top of the topic structure to provide namespace isolation for devices across different sites, such as the example below.

```
GB-LON-HQ/devices/FCU-1/events/pointset
US-NYC-HQ/devices/FCU-1/events/pointset
```
