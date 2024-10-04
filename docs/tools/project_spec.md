[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Project Spec](#)

# Project Specification

`//provider/project[/namespace][+user]`

* `provider`
  * `gbos`
  * `pref`
  * `mqtt`
  * `pubsub`
* `project`
  * GCP project
  * IoT Core project
* `namespace`
  * default
  * k8s namespace
  * pubsub namespace
* `user`
  * canonical users
  * individual users

* Examples

`//gbos/bos-platform-dev`
`//gbos/bos-platform-dev/peringknife`
`//mqtt/localhost`
`//pubsub/bos-platform-dev/peringknife+debug`
`//pref/bos-platform-dev`

* Tool Support

* registrar
  * not `pubsub`
* validator
  * allows `pubsub`
* sequencer
  * 
* pull_messages
  * `mqtt`
  * `pubsub`




