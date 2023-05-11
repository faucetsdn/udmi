#!/bin/bash

FILE_PATH=haproxy.cfg
BUCKET_NAME=@GCP_PROJECT_ID@-gcs

gsutil cp  $FILE_PATH gs://$BUCKET_NAME/$FILE_PATH