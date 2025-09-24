#!/bin/bash
# No -e so test can be cleaned up once it's complete
set -x
ROOT_DIR=$(dirname $(realpath $0))

export DN_SITE_PATH=$(realpath $1)
export DN_TARGET=//mqtt/localhost

bash $ROOT_DIR/../docker/bacnet_device/build.sh
bash $ROOT_DIR/../../bin/build_container test-discovery_node

cleanup() {
  # Deletes docker containers
  docker ps -a | grep 'discovery-integration' | awk '{print $1}' | xargs --no-run-if-empty docker stop
}
# Because ctrl+c-ing this test has a tendancy to skip if thrashed
trap cleanup INT

docker network create --subnet=192.168.12.0/24 --ip-range=192.168.12.0/24 --gateway=192.168.12.254 discovery-integration || true

for i in $(seq 1); do
  docker run --rm -d \
    --name=discovery-integration-$i \
    --network=discovery-integration\
    --ip=192.168.12.$i \
    -e BACNET_ID=$i \
    test-bacnet-device
done

SUMMARY=

TESTS=($(docker run --rm \
  --entrypoint=/venv/bin/python3 \
  --network=discovery-integration \
  -e I_AM_INTEGRATION_TEST=1 \
  test-discovery_node \
  -m pytest --collect-only -q tests/test_integration.py | grep '::test_'))

echo "Found ${#TESTS[@]} tests to run."

FINAL_EXIT_CODE=0
for test in "${TESTS[@]}"; do
  echo "Running test: $test"
  docker run --rm \
    --entrypoint=/venv/bin/python3 \
    --network=discovery-integration \
    -e I_AM_INTEGRATION_TEST=1 \
    test-discovery_node \
    -m pytest --capture=no --log-cli-level=INFO "$test" # Run the specific test

  TEST_EXIT_CODE=$?
  if [ $TEST_EXIT_CODE -ne 0 ]; then
    SUMMARY="${SUMMARY}${test}: FAIL\n"
    FINAL_EXIT_CODE=1 
  else
    SUMMARY="${SUMMARY}${test}: PASS\n"
  fi
done

cleanup

echo -e $SUMMARY

exit $FINAL_EXIT_CODE
