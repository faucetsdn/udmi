#!/bin/bash -e
ROOT_DIR=$(dirname $(realpath $0 ))
UDMI_ROOT=$(realpath $ROOT_DIR/../../../../..)
source $UDMI_ROOT/etc/shell_common.sh
cd $ROOT_DIR
retry_cmd docker build -t test-bacnet-device -f Dockerfile .