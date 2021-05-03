# Sequence Validator Setup

The UDMI sequence validator tool monitors a sequence of messages from a device's  stream and validates that the composition of sequential messsages is compliant with the UDMI Schema

1. Ensure you have deployed the necessary cloud functions to your GCP project _[(Guidance)](dashboard.md)_ 

2. Add a new GCP IoT Core registry with a registry ID of `UDMS-REFLECT`. This serves as a "reflector" of the IoT registry the site model is configured with, combining all messages published into a single stream. Use `udmi_reflect` as the default PUB/SUB topic for this registry

3. Add a new device to the UDMS-REFLECT registry with the followong configuration:
    * device_id: `<Registry ID as defined in Site Model>`
    * auth_key: `<PATH to ` _(Note that the device_id used here is the registry_id that the devices are connected to. Also, the auth_key to use is any valid auth key that matches key_file in the validator_config.json file. It is NOT the key for the device-under-test)._

4. Configure the sequence test by creating a file named `validator_config.json`, or updating the file if it exists  to match with a new device under test
```
{
  "project_id": "<PROJECT_ID>",
  "site_model": "<PATH_TO_SITE_MODEL>",
  "device_id": "<DEVICE_ID>",
  "serial_no": "<SERIAL_NO>",
  "key_file": "<UDMS_REFLECT_PRIVATE_KEY_PATH>"
}
```
* `<PROJECT_ID>` is the ID of the GCP project the registry is located within 
* `<PATH_TO_SITE_MODEL>` is the path to the site model 
* `<DEVICE_ID>` is the ID of the device under test
* `<SERIAL_NO>` is the serial number to be assossciated with state messages as a part of the test, is a random number prefixed by `sequence-`, e.g. `sequencer-18241`
* `<UDMS_REFLECT_PRIVATE_KEY_PATH>` is the path to the 


## Running Test Validator 
To run the sequence validator use the command
`java -cp validator/build/libs/validator-1.0-SNAPSHOT-all.jar org.junit.runner.JUnitCore com.google.daq.mqtt.validator.validations.BaselineValidator`

To run sequence testing suite with built-in pubber, run the `bin/setup_validator` to configure the test, and run `bin/test_sequences`. In a seperate window, run the commmand `tail -f pubber.out` to view the pubber output
 