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

- Manual creation of GCP IoT Core registries:
  - Create shadow site_model registry (e.g. registry `ZZ-TRI-FECTA%A`).
  - Create shadow alternate registry (e.g. registry `ZZ-REDIRECT-NA%A`), and if not some tests will be skipped.
  - Create shadow reflector registry device entries (e.g. device `ZZ-TRI-FECTA%A` in the `UDMS-REFLECT` registry).
    - Will need a public key, and the corresponding private key should be put in `sites/udmi_site_model/validator/rsa_private.pkcs8`
    - Also device `ZZ-REDIRECT-NA%A` in registry `UDMS-REFLECT` if necessary for endpoint connection testing (can use same key).
- Semi-automated population of registry entries:
  - Define `UDMI_REGISTRY_SUFFIX` env variable: either locally or through a GitHub Actions secret.
  - `bin/test_sequencer $PROJECT_ID no_valid_test` to attempt a test run -- this will fail.
    - Make sure the referenced registry exists: check log output for latest "creating client" line for exact path.
    - Should be a combination of the target project, registry from the site model, and env registry suffix. See examples above.
  - `validator/bin/registrar /tmp/validator_config.json`: the config file is generated from the failed `bin/test_sequencer` step.
    - This should automatically pick up the reflector suffix as defined by `UDMI_REGISTRY_SUFFIX`
  - `validator/bin/registrar /tmp/validator_config.json -a`: The added `-a` means _alternate_ for the option redirection test registry.
    - e.g., normally the registry would be `ZZ-TRI-FECTA%A`, but this should then be `ZZ-REDIRECT-NA%A`.

## CI Workflow

If the `UDMI_REGISTRY_SUFFIX` is defined as a GitHub Actions secret, the workflow will automatically skip `bin/test_sequencer` in the
main flow, and instead run it in a parallal sequencer-only flow. The "skipped" step will still show up but not executed.
