#!/bin/bash -e
site_path=site_model/
project_spec=//mqtt/mosquitto

bin/mapper GAT-123 provision
bin/mapper GAT-123 discover vendor
echo Waiting for discovery...
sleep 20
bin/registrar $site_path $project_spec
bin/mapper GAT-123 map vendor
bin/mapper GAT-123 discover
echo Waiting for discovery...
sleep 20