#!/bin/bash
tag=latest
PROJECT_ID=@GCP_PROJECT_ID@

docker build -t syslog:$tag .
docker tag syslog:$tag gcr.io/$PROJECT_ID/syslog:$tag
docker push gcr.io/$PROJECT_ID/syslog:$tag

echo gcr.io/$PROJECT_ID/syslog:$tag