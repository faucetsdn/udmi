[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Message Walk](#)

# Illustrative Message Walk

This document details a high-level overview of the path a message takes as it works its way
through the UDMI framework. It is not a detailed _how-to_, but rather an overview guide
of the various bits and pieces. This generally assumes a reasonable working knowledge of the
individual components involved, which typically have more detailed documentation elsewhere.

## Site Model

The [UDMI Site Model](site_model.md) provides an abstract model for what the site should
look like. Specifically, it holds information used by other tools to do things relating to the device.

## Pubber

[Pubber](../tools/pubber.md) is a reference device that can pretend to be any device as listed in the UDMI site
model. It's important for system testing to isolate the setup from anything external. Usually,
if Pubber doesn't work then there's something wrong with the code or GCP configuration,
otherwise it indicates a problem with the real/actual device.

## IoT Core

[IoT Core](https://cloud.google.com/iot/docs/) is the externally-facing endpoint for on-prem devices.
It organizes a particular device into a {&nbsp;_project_, _registry_&nbsp;} grouping. The important
configuration parameters for a given _registry_ are the _Default telemetry_ and _Device state_ topic
types, which should be set to _udmi\_target_ and _udmi\_state_, respectively.

## PubSub Topics

[PubSub](https://cloud.google.com/pubsub/docs/) is used as the primary communication mechanism for
messages in the GCP project. Note that PubSub uses the term _topics_ in a way that is similar to,
but semantically different than, an MQTT _topic_. Same word, roughly same meaning, different contexts.
There are four main PubSub topics used by the UDMI system for various functions:

* __udmi\_target__: Primary topic for device communication, including the main _telemetry_ publish events.
but also, e.g., augmented _state_ messages, _config_, etc... Subscribing to this topic should give
a reasonably complete stream of all device-related traffic.
* __udmi\_state__: Interstitial topic used for augmenting incoming _state_ messages with aux information,
followed by a simple re-publish to the _udmi\_target_ topic. No need to pay much attention to this one.
* __udmi\_config__: Used for processing incremental _config_ message updates. I.e., for a given subblock
such as _pointset_, this will process it appropriately so that it is combined into a complete top-level
_config_ block.
* __udmi\_reflect__: Used internally by the reflection capability (see below). Not something to pay much
attention to.

## Cloud Functions

The first set of [Cloud Functions](https://cloud.google.com/functions/docs/) handle the ingest traffic
from a device. The [source code](../../udmis/functions/) for these functions can be published
to the cloud project by the `udmis/deploy_udmis_gcloud` command (see below).

* __udmi\_target__: Processes incoming device _event_ messages and writes them to the designated
location in the Firestore database. 
* __udmi\_state__: Processes incoming device _state_ messages and re-writes them to the _udmi\_target_
topic, and also shards them out by subsystem to various sub-parts of Firestore.

## Validator

The [UDMI Validator](../tools/validator.md) tool monitors a device's message stream and validates messages that it sees against
the UDMI schema. There are a variety of configurations used, depending on the overall intent.

## Sequence Validator

The [UDMI Sequence Validator](../tools/sequencer.md) tool monitors a sequence of messages from a device's 
stream and validates that the composition of sequential messages is compliant with the UDMI Schema.
