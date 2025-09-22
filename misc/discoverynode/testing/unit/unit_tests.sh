#!/bin/bash
ROOT_DIR=$(dirname $(realpath $0))

bash $ROOT_DIR/../../bin/build_container test-discovery_node

docker run --rm \
  --entrypoint=/venv/bin/python3 \
  test-discovery_node \
  -m pytest --capture=no --log-cli-level=INFO --ignore=tests/test_integration.py tests

