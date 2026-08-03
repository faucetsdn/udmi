#!/bin/bash -e
site_path=site_model
project_spec=//mqtt/mosquitto
discoveryNodeIp=$1

bin/mapper $discoveryNodeIp provision
bin/mapper $discoveryNodeIp discover vendor
echo Waiting 30s for the discovery...
sleep 30
bin/registrar $site_path $project_spec

echo "Changing the address for AHU-22 device to 0x68"
device_metadata=$site_path/devices/AHU-22/metadata.json
jq '.localnet.families.vendor.addr = "0x68"' $device_metadata | sponge $device_metadata

bin/mapper $discoveryNodeIp map vendor
bin/mapper $discoveryNodeIp discover