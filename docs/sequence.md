# Sequence Validator Setup

The UDMI sequence validator tool monitors a sequence of messages from a device's  stream and validates that the composition of sequential messsages is compliant with the UDMI Schema

1. Ensure you have deployed the necessary cloud functions to your GCP project _[(Guidance)](dashboard.md)_ 

2. Run `bin/setup_validator <GCP_PROJECT>` to configure your system locally for the (This will create a validator_config.json file)

3. Add a new GCP IoT Core registry with a registry ID of `UDMS-REFLECT`. This serves as a "reflector" of the IoT registry the site model is configured with, combining all messages published into a single stream.

4. Add a new device to the UDMS-REFLECT registry with the followong configuration:
    * device_id: `<Registry ID as defined in Site Model>`
    * auth_key: `udmi_site_model/devices/AHU-1/rsa_public.pem` _(Note that the device_id used here is the registry_id that the devices are connected to. Also, the auth_key to use is any valid auth key that matches key_file in the validator_config.json file. It is NOT the key for the device-under-test)._

## Running Test Validator 
Run sequence testing suite with built-in pubber: `bin/test_sequences`
 