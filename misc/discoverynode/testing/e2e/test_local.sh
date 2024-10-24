#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0))

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --subnet=192.168.11.0/24 --ip-range=192.168.11.0/24 --gateway=192.168.11.254 discoverynode-network || true

python3 -m pytest --log-cli-level=INFO $ROOT_DIR/test_local.py
