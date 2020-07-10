#!/bin/bash -e

ROOT=$(dirname $0)/../..
cd $ROOT

CONFIG=local/pubber.json

DEVICE=$1
DATA=$2
PROJECT=`jq -r .projectId $CONFIG`
REGION=`jq -r .cloudRegion $CONFIG`
REGISTRY=`jq -r .registryId $CONFIG`
TOPIC=target
SUBFOLDER=config

if [ ! -f "$DATA" ]; then
    echo Missing device or config file $DATA
    echo Usage: $0 [device] [config_message]
    false
fi

echo Configuring $PROJECT:$REGION:$REGISTRY:$DEVICE from $DATA

ATTRIBUTES="subFolder=$SUBFOLDER,deviceId=$DEVICE,deviceRegistryId=$REGISTRY"
ATTRIBUTES+=",deviceNumId=$RANDOM,projectId=$PROJECT"

gcloud pubsub topics publish $TOPIC --project=$PROJECT \
       --attribute=$ATTRIBUTES \
       --message "$(< $DATA)"

gcloud iot devices configs update \
       --project=$PROJECT \
       --region=$REGION \
       --registry=$REGISTRY \
       --device=$DEVICE \
       --config-file=$DATA
