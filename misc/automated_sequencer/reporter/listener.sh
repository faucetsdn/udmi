#!/bin/bash -ex
ROOT_DIR=$(realpath $(dirname $0))
echo listening
source $ROOT_DIR/config.sh
while true
do
	pubsub=$(gcloud pubsub subscriptions pull $PUBSUB --limit=1 --format=json --project=$PROJECT_ID)
    if [[ $pubsub != '[]' ]]; then
        ACK_ID=$(echo $pubsub | jq -r '.[0].ackId')
        PAYLOAD=$(echo $pubsub | jq -r '.[0].message.data' | base64 -d)
        
        DEVICE_PATH=$(echo $PAYLOAD | jq -r '.device_path')
        echo "processing $DEVICE_PATH"
        
        $ROOT_DIR/generate_device_report $GCS_BUCKET $DEVICE_PATH report.html
        # Upload report
        gsutil cp report.html gs://$GCS_BUCKET/$GCS_SUMMARY_SUBDIR/$DEVICE_PATH/report.html

        # Generate master report
        gsutil cp gs://$GCS_BUCKET/$MASTER_REPORT_NAME index.html || cp $ROOT_DIR/index_template.html index.html
        python3 $ROOT_DIR/python/merge_master.py $MASTER_REPORT_NAME $DEVICE_REPORT_NAME
        gsutil cp index.html gs://$GCS_BUCKET/$MASTER_REPORT_NAME
        
        gcloud pubsub subscriptions ack $PUBSUB --ack-ids=$ACK_ID --project=$PROJECT_ID
    fi
    sleep 1
done