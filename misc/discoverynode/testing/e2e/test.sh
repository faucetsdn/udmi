#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0))

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../docker/discovery_node/build.sh

python3 -m pytest -v --capture=no --log-cli-level=INFO e2e_test.py