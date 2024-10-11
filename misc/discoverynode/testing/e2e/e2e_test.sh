#!/bin/bash -e
set -o xtrace 


ROOT_DIR=$(dirname $(realpath $0))
DEVICE_CONFIGS=$ROOT_DIR/docker_config.json

DEVICEID=GAT-123

( site_path=sites/udmi_site_model; project_spec=//gbos/bos-platform-dev;cd ~/udmi && bin/registrar $site_path $project_spec -x -d && bin/registrar $site_path $project_spec )


bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh


docker network create discoverynode-network || true

test_devices=$(jq -r 'keys[]' $DEVICE_CONFIGS)
for device in $test_devices; do
  image=$(jq -r .$device.image $DEVICE_CONFIGS)
  ethmac=$(jq -r .$device.ethmac $DEVICE_CONFIGS)
  bacnet_id=$(jq -r .$device.bacnet_id $DEVICE_CONFIGS)

  docker run --rm -d \
    --name discoverynode-test-$device \
    --mac-address=$ethmac \
    --network=discoverynode-network \
    -e BACNET_ID=$bacnet_id \
    $image

done


( site_path=sites/udmi_site_model; project_spec=//gbos/bos-platform-dev;cd ~/udmi && bin/registrar $site_path $project_spec -x -d && bin/registrar $site_path $project_spec )

docker run -d --rm\
  --name discoverynode-test-node \
  --network=discoverynode-network \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  --mount type=bind,source=$ROOT_DIR/config.toml,target=/usr/src/app/config.toml \
  --mount type=bind,source=$ROOT_DIR/ec_private.pem,target=/usr/src/app/ec_private.pem \
  test-discovery_node python3 main.py --config_file=config.toml

site_path=sites/udmi_site_model
project_spec=//gbos/bos-platform-dev

cd ~/udmi

cat $site_path/devices/$DEVICEID/metadata.json | jq -r '.discovery|={"families": {"bacnet":{}}}' | sponge $site_path/devices/$DEVICEID/metadata.json 

# Wait for Clearblade ..
sleep 10

echo Enable the gateway to auto-provision
bin/mapper $DEVICEID provision

echo Kick off a discovery run for the gateway
bin/mapper $DEVICEID discover

echo Waiting for discovery...
sleep 20

echo Extracting results at $(date -u -Is)
bin/registrar $site_path $project_spec


docker logs discoverynode-test-node > node.logs

## Shutdown
docker ps -a | grep "discoverynode-test-" | awk '{print $1}' | xargs docker stop
echo error=$error
exit $error