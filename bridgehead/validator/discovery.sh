#!/bin/bash -e
site_path=site_model/
project_spec=//mqtt/mosquitto
discoveryNodeIp=GAT-123

bin/mapper $discoveryNodeIp provision
bin/mapper $discoveryNodeIp discover vendor
echo Waiting 20s for the discovery...
sleep 20
bin/registrar $site_path $project_spec
bin/mapper $discoveryNodeIp map vendor
bin/mapper $discoveryNodeIp discover