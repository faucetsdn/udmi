#!/bin/bash
tag=latest
image_name=autosequencer-reporter
PROJECT_ID=bos-platform-artifacts
REGISTRY=us-central1-docker.pkg.dev/$PROJECT_ID/udmi

docker build --no-cache -t $image_name:$tag .
docker tag $image_name:$tag $REGISTRY/$image_name:$tag
docker push $REGISTRY/$image_name:$tag