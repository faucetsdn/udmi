#!/bin/bash
tag=latest
PROJECT_ID=@GCP_PROJECT_ID@

docker build -t mqttproxy:$tag .
docker tag mqttproxy:$tag gcr.io/$PROJECT_ID/mqttproxy:$tag
docker push gcr.io/$PROJECT_ID/mqttproxy:$tag
