#!/bin/bash
tag=latest
image_name=autosequencer-sequencer
PROJECT_ID=$1
REGISTRY=us-central1-docker.pkg.dev/$PROJECT_ID/udmi

docker build --no-cache -t $image_name:$tag .
docker tag $image_name:$tag $REGISTRY/$image_name:$tag
docker push $REGISTRY/$image_name:$tag