#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0 ))
cd $ROOT_DIR
docker build -t test-bacnet-device -f Dockerfile .