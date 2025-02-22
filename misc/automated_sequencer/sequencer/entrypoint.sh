#!/bin/bash -x
# no -e because it's fine for some things to fail - the reporting is in place for this
ROOT_DIR=$(realpath $(dirname $0))

git clone https://github.com/$UDMI_REPO/udmi.git 
(cd udmi && git checkout $UDMI_BRANCH)

source $ROOT_DIR/config.sh

TIMESTAMP=$(date +%s)

VERSION_OUT_FILE=/$VERSION_FILE_NAME
RUNLOG=/tmp/run.log

touch /$VERSION_FILE_NAME 

# Env vars/args
git -C /udmi pull
cat <<EOF >$VERSION_OUT_FILE
{
    "TIMESTAMP": "$TIMESTAMP",
    "version": "$(git -C /udmi log --pretty=format:'%h' -n 1)"
}
EOF

# Device Path is the directory structure, project/registry/device_id
DEVICE_PATH=$PROJECT_ID/$SITE_NAME_REPO/$DEVICE_ID

# GCS_PATH summary is where the results.log and "version.json" marker go
GCS_PATH_SUMMARY=gs://$GCS_BUCKET/$GCS_SUMMARY_SUBDIR/$DEVICE_PATH/$TIMESTAMP
GCS_PATH_RESULTS=gs://$GCS_BUCKET/$GCS_RESULTS_SUBDIR/$DEVICE_PATH

gsutil cp $VERSION_OUT_FILE $GCS_PATH_SUMMARY/$VERSION_FILE_NAME

## Checkout Site Model
gcloud source repos clone $SITE_NAME_REPO --project=$PROJECT_ID
cd $SITE_NAME_REPO
git checkout $BRANCH

# Delete old results so when we zip the site model, it only contains resuls from the current run
cd $SITE_MODEL_SUBDIR
rm -rf out/ 
ls out || true

# mkdir so we can write and tar this directory
mkdir -p out/$DEVICE_ID

SITE_MODEL_PATH=$(realpath /$SITE_NAME_REPO/$SITE_MODEL_SUBDIR)
CLOUD_IOT_CONFIG=$SITE_MODEL_PATH/cloud_iot_config.json

REGISTRY_ID=$(jq -r '.registry_id' $SITE_MODEL_PATH/cloud_iot_config.json)

jq -r ".device_id=\"$DEVICE_ID\"" $CLOUD_IOT_CONFIG | sponge $CLOUD_IOT_CONFIG

cd /udmi

# need to build as root, maybe because I'm in the root directory
sudo validator/bin/build

# update before pull because missing packages 
sudo apt update
sudo bin/setup_base

# Run with timeout because sequencer might not exit on error
timeout 3600 bin/sequencer $SITE_MODEL_PATH $SEQUENCER_PROJECT_ID $DEVICE_ID 2>&1 | tee $RUNLOG

gsutil cp $SITE_MODEL_PATH/out/devices/$DEVICE_ID/RESULT.log $GCS_PATH_SUMMARY/RESULT.log
gsutil cp $SITE_MODEL_PATH/out/devices/$DEVICE_ID/results.md $GCS_PATH_SUMMARY/results.md

cp $RUNLOG $SITE_MODEL_PATH/out/devices/$DEVICE_ID/run.log
tar -C $SITE_MODEL_PATH/out/devices/$DEVICE_ID/ -zcvf output.tar.gz .

gsutil cp output.tar.gz $GCS_PATH_RESULTS/$TIMESTAMP.tar.gz

PUBSUB_PAYLOAD=$(jq -n ".device_path+=\"$DEVICE_PATH\" | .complete+=true" )
gcloud pubsub topics publish $PUBSUB --message="$PUBSUB_PAYLOAD" --attribute="deviceId=ads" --attribute="deviceRegistryId=blah" --project=$PUBSUB_PROJECT
