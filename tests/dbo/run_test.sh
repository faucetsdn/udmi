#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ROOT_DIR="$( cd "$DIR/../../.." &> /dev/null && pwd )"

# Activate the venv created by bin/setup_base
if [ -f "$ROOT_DIR/venv/bin/activate" ]; then
    source "$ROOT_DIR/venv/bin/activate"
fi

# Test merge: building_config.yaml -> site_model
TEST_SITE_MODEL="$DIR/test_site_model"
rm -rf "$TEST_SITE_MODEL"
$ROOT_DIR/bin/dbo_merge $DIR/building_config.yaml "$TEST_SITE_MODEL"

# Test extract: site_model -> extracted_building_config.yaml
$ROOT_DIR/bin/dbo_extract "$TEST_SITE_MODEL" $DIR/extracted_building_config.yaml

echo "Original building_config.yaml:"
cat $DIR/building_config.yaml
echo "-----------------------------------"
echo "Extracted extracted_building_config.yaml:"
cat $DIR/extracted_building_config.yaml
echo "-----------------------------------"

# Diff
if diff -b -w $DIR/building_config.yaml $DIR/extracted_building_config.yaml; then
    echo "SUCCESS: The extracted building_config.yaml matches the original perfectly!"
else
    echo "FAILURE: The extracted building_config.yaml does not match the original!"
    false
fi
