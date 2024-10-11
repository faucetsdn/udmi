#!/bin/bash -e

ROOT_DIR=$(dirname $(realpath $0))
DEVICE_CONFIGS=$ROOT_DIR/docker_config.json
UDMI_DIR=$(realpath $ROOT_DIR/../../../../)

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create discoverynode-network || true

test_devices=$(jq -r 'keys[]' $DEVICE_CONFIGS)
for device in $test_devices; do
  image=$(jq -r .$device.image $DEVICE_CONFIGS)
  ethmac=$(jq -r .$device.ethmac $DEVICE_CONFIGS)
  bacnet_id=$(jq -r .$device.bacnet_id $DEVICE_CONFIGS)

  echo starting $device
  (docker run -it --rm -d \
    --name discoverytest-$device \
    --mac-address=$ethmac \
    --network=discoverynode-network \
    -h $device.local \
    -e BACNET_ID=$bacnet_id \
    $image > /dev/null) &

done

docker run -d --rm\
  --name discoverynode-test-node \
  --network=discoverynode-network \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  --mount type=bind,source=$ROOT_DIR/sequencer_config.toml,target=/usr/src/app/config.toml \
  --mount type=bind,source=$UDMI_DIR/sites/udmi_site_model/devices/AHU-1/rsa_private.pem,target=/usr/src/app/rsa_private.pem \
  -v  $PWD/pcap:/pcap \
  test-discovery_node python3 main.py --config_file=config.toml || error=1

cat ~/udmi/sites/udmi_site_model/devices/AHU-1/metadata.json | jq '.discovery.families={"vendor":{}}'

cd $UDMI_DIR
bin/sequencer -v sites/udmi_site_model //gbos/bos-platform-testing AHU-1 123 single_scan_future || true

docker logs discoverynode-test-node 

docker ps -a | grep "discoverynode-" | awk '{print $1}' | xargs docker stop

tail -n 30 out/sequencer.log | grep "Failed check that all expected addresses were found; 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0x65, 28179023, 20231"