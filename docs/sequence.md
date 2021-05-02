# UDMI Sequence Validator 

The sequence validator is a tool which is used to validate that a sequence of events are compliant with the UDMI specification 





Deploy necessary cloud functions to your GCP project
dashboard/deploy_dashboard ${FIREBASE_PROJECT}
Start to configure your system locally:
bin/setup_validator ${GCP_PROJECT}
(This will create a validator_config.json file)
Configure a new GCP "reflector" registry/device entry:
registry_id: UDMS-REFLECT
device_id: ${REGISTRY_ID}
auth_key: udmi_site_model/devices/AHU-1/rsa_public.pem
(Note that the device_id used here is the registry_id that the devices are connected to. Also, the authKey to use is any valid auth key that matches key_file in the validator_config.json file. It is NOT the key for the device-under-test).
Run sequence testing suite with built-in pubber:
bin/test_sequences

Testing on MacOS
I tried to deploy the dashboard on MacOS and it complained realpath missing

$ dashboard/deploy_dashboard iot-workshop-2020
dashboard/deploy_dashboard: line 8: realpath: command not found

It should be fixed using

$ brew install coreutils
