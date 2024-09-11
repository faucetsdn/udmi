#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0 ))
cd $ROOT_DIR/../../../src
docker build -t test-discovery_node -f $ROOT_DIR/Dockerfile .
