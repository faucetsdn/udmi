#!/bin/sh
#https://github.com/haproxytech/haproxy-docker-ubuntu/tree/main/2.8
PATH=/usr/bin
FILE_PATH=cb_list.txt #url encoded if in subdir
OUT=/usr/local/etc/haproxy/gcs_cb_list.txt 
BUCKET_NAME=@GCP_PROJECT_ID@-gcs
CONFIG=haproxy.cfg
CONFIG_OUT=/usr/local/etc/haproxy/gcs_haproxy.cfg

ACCESS_TOKEN=`$PATH/curl \
"http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" \
-H "Metadata-Flavor: Google" | $PATH/jq -r '.access_token'`

$PATH/curl -f -X GET \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -o $OUT \
  --max-time 5 \
  "https://storage.googleapis.com/storage/v1/b/${BUCKET_NAME}/o/${FILE_PATH}?alt=media"


$PATH/curl -f -X GET \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -o $CONFIG_OUT \
  --max-time 5 \
  "https://storage.googleapis.com/storage/v1/b/${BUCKET_NAME}/o/${CONFIG}?alt=media"

echo server $SYSLOG_SERVICE_HOST
SYSLOG_ADDR="${SYSLOG_SERVICE_HOST:-127.0.0.1}"
echo using $SYSLOG_ADDR
sed -i "s/@SYSLOG_ADDR@/$SYSLOG_ADDR/" $CONFIG_OUT

$PATH/cat $CONFIG_OUT

echo ".."


# Launch HAProxy
set -e
/usr/sbin/haproxy -W -db -f $CONFIG_OUT
