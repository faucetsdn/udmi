#!/bin/bash -e
set -o xtrace

ROOT_DIR=$(dirname $(realpath $0))
DEVICE_CONFIGS=$ROOT_DIR/docker_config.json

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create test-network2 || true

docker run -it --rm\
  --name discoverynode-blah \
  --network=test-network2 \
  --mount type=bind,source=$ROOT_DIR/docker_config.json,target=/usr/src/app/docker_config.json \
  --mount type=bind,source=$ROOT_DIR/sequencer_config.toml,target=/usr/src/app/config.toml \
  --mount type=bind,source=$ROOT_DIR/rsa_private.pem,target=/usr/src/app/rsa_private.pem \
  -v  $PWD/pcap:/pcap \
  test-discovery_node python3 main.py config.toml|| error=1

docker ps -a | grep "discoverynode-test-" | awk '{print $1}' | xargs docker stop
echo error=$error
exit $error