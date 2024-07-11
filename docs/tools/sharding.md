[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Sharding](#)

# Sharding Sequencer

It's possible to run sequencer on an alternate set of project registries, called shards. This is
useful for testing things from the same site model in parallel, since currently there can only
be one instance of any tool using the reflector running on each registry.

## Operation

Basic operation is determined by a specified _registry suffix_ (e.g. `_A`) that triggers all (ideally) tools
to automatically append the suffix to any actual registry operation. Tools should automatically pick this
up from the `UDMI_REGISTRY_SUFFIX` env variable, and if it's not set then it will default back to normal
(no suffix) behavior.

E.g., on tool startup, instead of the normal MQTT client connection message:

```
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-REDIRECT-NA creating client projects/bos-peringknife-dev/locations/us-central1/registries/UDMI-REFLECT/devices/ZZ-REDIRECT-NA on ssl://mqtt.googleapis.com:8883
```
a system with a registry suffix setting of `_A` would show:
```
[main] INFO com.google.bos.iot.core.proxy.MqttPublisher - ZZ-REDIRECT-NA_A creating client projects/bos-peringknife-dev/locations/us-central1/registries/UDMI-REFLECT/devices/ZZ-REDIRECT-NA_A on ssl://mqtt.googleapis.com:8883
```

## Setup

- Manual creation of registries:
  - Create shadow site_model registry (e.g. registry `ZZ-TRI-FECTA_A`).
  - Create shadow alternate registry (e.g. registry `ZZ-REDIRECT-NA_A`).
  - Create shadow reflector device entry (e.g. device `ZZ-TRI-FECTA_A` in the `UDMI-REFLECT` registry).
    - Should use the same public/private keys as the base device (no `_A`).
  - Create device `ZZ-REDIRECT-NA_A` in registry `UDMI-REFLECT` if necessary for endpoint connection testing.
- Semi-automated population of registry entries:
  - Define `UDMI_REGISTRY_SUFFIX` env variable: either locally or through a GitHub Actions variable.
  - `bin/test_sequencer $PROJECT_ID no_valid_test` to attempt a test run -- this will fail.
    - Make sure any referenced registry exists: check log output for latest "creating client" line for exact path.
    - Should be a combination of the target project, registry from the site model, and env registry suffix. See examples above.
  - Populate registry devices with `validator/bin/registrar /tmp/validator_config.json`
    - The `/tmp/validator_config.json` config file is generated from the failed `bin/test_sequencer` step.
  - `validator/bin/registrar /tmp/validator_config.json -a`: The added `-a` means _alternate_ for the option redirection test registry.
    - Value for alternate registry will be in the `/tmp/validator_config.json` config file.
    - e.g., normally the registry would be `ZZ-TRI-FECTA_A`, but this should then be `ZZ-REDIRECT-NA_A`.

## CI Workflow

If the `UDMI_REGISTRY_SUFFIX` is defined as a GitHub Actions variable, the workflow will automatically skip `bin/test_sequencer` in the
main flow, and instead run it in a parallel sequencer-only flow. The "skipped" step will still show up but not executed.
