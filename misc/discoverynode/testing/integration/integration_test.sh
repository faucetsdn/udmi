#!/bin/bash
# No -e so test can be cleaned up once it's complete
set -x
ROOT_DIR=$(dirname $(realpath $0))

export DN_SITE_PATH=$(realpath $1)
export DN_TARGET=//mqtt/localhost

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../../bin/build_container test-discovery_node

docker network create --subnet=192.168.12.0/24 --ip-range=192.168.12.0/24 --gateway=192.168.12.254 discovery-integration || true

for i in $(seq 1); do
  docker run --rm -d \
    --name=discovery-integration-$i \
    --network=discovery-integration\
    --ip=192.168.12.$i \
    -e BACNET_ID=$i \
    test-bacnet-device
done

docker run -it --rm \
  --entrypoint=/venv/bin/python3 \
  --network=discovery-integration \
  -e I_AM_INTEGRATION_TEST=1 \
  test-discovery_node \
  -m pytest --capture=no --log-cli-level=INFO tests/test_integration.py

EXIT_CODE=$?

docker ps -a | grep 'discovery-integration' | awk '{print $1}' | xargs docker stop

exit $EXIT_CODE