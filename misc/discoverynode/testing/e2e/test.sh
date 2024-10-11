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
IFS='/' read -ra bits <<< "$TARGET"

if [[ ${bits[2]} != gbos ]]; then
  echo only gbos supported
  exit 1
fi

export DN_TARGET=$TARGET
export DN_GCP_PROJECT=${bits[3]}
export DN_REGISTRY=$REGISTRY

if [[ -z ${bits[5]} ]]; then
  export DN_MQTT_REGISTRY=$REGISTRY_NAME~${bits[5]}
else
  export DN_MQTT_REGISTRY=$REGISTRY_NAME
fi

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

docker network create --subnet=192.168.11.0/24 --ip-range=192.168.11.0/24 --gateway=192.168.11.254 discoverynode-network || true

python3 -m pytest -v --capture=no --log-cli-level=INFO $ROOT_DIR/e2e_test.py
