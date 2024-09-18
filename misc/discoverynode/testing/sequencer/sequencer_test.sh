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

docker run -d --rm\
  --name discoverytest-node \
  --network=discovery-network \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  --mount type=bind,source=$ROOT_DIR/sequencer_config.toml,target=/usr/src/app/config.toml \
  --mount type=bind,source=sites/udmi_site_model/devices/AHU-1/rsa_private.pem,target=/usr/src/app/rsa_private.pem \
  -v  $PWD/pcap:/pcap \
  test-discovery_node python3 main.py config.toml || error=1

cat ~/udmi/sites/udmi_site_model/devices/AHU-1/metadata.json | jq '.discovery.families={"bacnet":{},"ipv4":{}}'

bin/sequencer sites/udmi_site_model //gbos/bos-platform-testing AHU-1 123 single_scan_past || true

docker ps -a | grep "discoverytest" | awk '{print $1}' | xargs docker stop

tail -n 30 out/sequencer.log 