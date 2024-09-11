#!/bin/bash -e
set -o xtrace

ROOT_DIR=$(dirname $(realpath $0))
DEVICE_CONFIGS=$ROOT_DIR/docker_config.json

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --internal test-network || true

test_devices=$(jq -r 'keys[]' $DEVICE_CONFIGS)
for device in $test_devices; do
  image=$(jq -r .$device.image $DEVICE_CONFIGS)
  ethmac=$(jq -r .$device.ethmac $DEVICE_CONFIGS)
  bacnet_id=$(jq -r .$device.bacnet_id $DEVICE_CONFIGS)

  docker run -it --rm -d \
    --name discoverynode-test-$device \
    --mac-address=$ethmac \
    --network=test-network \
    -e BACNET_ID=$bacnet_id \
    $image

done

docker run -it --rm\
  --name discoverynode-blah \
  --network=test-network \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  test-discovery_node || error=1

docker ps -a | grep "discoverynode-test-" | awk '{print $1}' | xargs docker stop
echo error=$error
exit $error