[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Project Spec](#)

# Project Specification

`//provider/project[/namespace][+user]`

* `provider`: Primary providers supported by the tools
  * `gbos`: Uses reflector client through IoT Core
  * `gref`: Uses reflector client through GCP PubSub
  * `mqtt`: Uses reflector client through MQTT broker
  * `pubsub` Uses direct access through PubSub (only works with `validator`)
* `project`: Meaning depends on the provider
  * `gbos`: IoT Core project id
  * `gref`, `pubsub`: GCP project id
  * `mqtt`: Broker hostname (currently only `localhost` fully supported)
* `namespace`: Allows multiple parallel instances for a given project
  * Automatically prefixed to necessary resources
  * Defaults to an _empty_ prefix
* `user`: Allows multiple concurrent users on the same project
  * `gbos`, `mqtt`: Not supported, will cause runtime error
  * `gref`, `pubsub`: Defaults to `debug` if not specified

* Examples

* `//gbos/bos-platform-dev`
  * IoT Core: _project_: `bos-platform-dev`, _registry_: `UDMI-REFLECT`, _device_: `ZZ-TRI-FECTA`
* `//gbos/bos-platform-dev+debug` (only one _client_ is currently allowed using `gbos`)
  * Error: `user name not supported for provider gbos`
* `//gbos/bos-platform-dev/faucetsdn`
  * IoT Core: _project_: `bos-platform-dev`, _registry_: `faucetsdn~UDMI-REFLECT`, _device_: `faucetsdn~ZZ-TRI-FECTA`
* `//gref/bos-platform-dev` (if no _user_ is supplied, it defaults to `debug`)
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_reply+debug`
* `//gref/bos-platform-dev+username`
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_reply+username`
* `//gref/bos-platform-dev/faucetsdn+username`
  * PubSub topic: `projects/bos-platform-dev/topics/faucetsdn~udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/faucetsdn~udmi_reply+username`
* `//mqtt/localhost`
  * MQTT client: `/r/UDMI-REFLECT/d/ZZ-TRI-FECTA`
* `//mqtt/localhost+debug` (only one _client_ is currently allowed using `mqtt`)
  * Error: `user name not supported for provider mqtt`
* `//pubsub/bos-platform-dev` (only works for `validator`)
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_target+debug`
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_target`
* `//pubsub/bos-platform-dev/faucetsdn+username` (only works for `validator`)
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/faucetsdn~udmi_target+username`
  * PubSub topic: `projects/bos-platform-dev/topics/faucetsdn~udmi_target`
