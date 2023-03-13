[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Sharding](#)

# Sharding Sequencer

It's possible to run sequencer on an alternate set of project registries, called shards. This is
useful for testing things from the same site model in parallel, since currently there can only
be one instance of any tool using the reflector running on each registry.

## Operation

A defined shard _registry suffix_ (e.g. `%A`), triggers all (ideally) tools to automatically append the suffix to any actual registry operation.
Tools should automatically pick this up from the `UDMI_REGISTRY_SUFFIX` env variable, and if it's not set then it will
default back to normal (no suffix) behavior.

E.g., on tool startup, instead of the normal MQTT client connection message:

```
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-REDIRECT-NA creating client projects/bos-peringknife-dev/locations/us-central1/registries/UDMS-REFLECT/devices/ZZ-REDIRECT-NA on ssl://mqtt.googleapis.com:8883
```
a system with a registry suffix setting of `%A` would show:
```
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-REDIRECT-NA%A creating client projects/bos-peringknife-dev/locations/us-central1/registries/UDMS-REFLECT/devices/ZZ-REDIRECT-NA%A on ssl://mqtt.googleapis.com:8883
```

## Setup

- Creating shadow site_model registry (e.g. registry `ZZ-TRI-FECTA%A`).
- Creating shadow alternate registry (e.g. registry `ZZ-REDIRECT-NA%A`), and if not some tests will be skipped.
- Creating shadow reflector registry device entries (e.g. device `ZZ-TRI-FECTA%A` in the `UDMS-REFLECT` registry).
  - Also device `ZZ-REDIRECT-NA%A` in `UDMS-REFLECT` if necessary for endpoint connection testing.
- Define `UDMI_REGISTRY_SUFFIX` env variable: either locally or through a GitHub Actions secret.
- `bin/registrar` with `-a` flag to populate the `ZZ-TRI-FECTA%A` (and maybe `ZZ-REDIRECT-NA%A`) with site devices.

