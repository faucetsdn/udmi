#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0))

export DN_SITE_PATH=$1

if [[ -z $DN_SITE_PATH ]]; then
  echo "Usage $0 SITE_PATH [TEST_CASES]"
  exit 1
fi

if [[ -z $2 ]]; then
  TEST_CASES=-k $2
fi

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --subnet=192.168.11.0/24 --ip-range=192.168.11.0/24 --gateway=192.168.11.254 discoverynode-network || true

python3 -m pytest --log-cli-level=INFO $TEST_CASES $ROOT_DIR/test_local.py
