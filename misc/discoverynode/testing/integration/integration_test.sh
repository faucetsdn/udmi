#!/bin/bash -e
#set -o xtrace

ROOT_DIR=$(dirname $(realpath $0))
DEVICE_CONFIGS=$ROOT_DIR/docker_config.json

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --ip-range=192.168.16.0/24 --subnet=192.168.16.0/24  discovery-network || true

test_devices=$(jq -r 'keys[]' $DEVICE_CONFIGS)
for device in $test_devices; do
  image=$(jq -r .$device.image $DEVICE_CONFIGS)
  ethmac=$(jq -r .$device.ethmac $DEVICE_CONFIGS)
  bacnet_id=$(jq -r .$device.bacnet_id $DEVICE_CONFIGS)

  echo starting $device
  (docker run -it --rm -d \
    --name discoverytest-$device \
    --mac-address=$ethmac \
    --network=discovery-network \
    -h $device.local \
    -e BACNET_ID=$bacnet_id \
    $image > /dev/null) &

done

echo starting discovery node

docker run --rm\
  --name discoverytest-node \
  --network=discovery-network \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  -v  $PWD/pcap:/pcap \
  test-discovery_node \
  python3 -m pytest -s --log-cli-level=INFO tests/test_integration.py || error=1

docker ps -a | grep "discoverytest" | awk '{print $1}' | xargs docker stop
echo error=$error
exit $error