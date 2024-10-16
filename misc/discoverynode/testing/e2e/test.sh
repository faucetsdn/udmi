#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0))

REGISTRY="ZZ-TRI-FECTA"

if [[ -z $1 ]]; then
  echo Usage $0 TARGET
  echo
  echo Example: ./test.sh //gbos/bos-platform-testing
  exit 1
fi

TARGET=$1

export DN_TARGET=$TARGET
export DN_REGISTRY=$REGISTRY

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --subnet=192.168.11.0/24 --ip-range=192.168.11.0/24 --gateway=192.168.11.254 discoverynode-network || true

python3 -m pytest -v --log-cli-level=INFO $ROOT_DIR/e2e_test.py
