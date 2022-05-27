 #!/bin/bash line

echo "Executing build..."
source ./build.sh
cd dist

echo "Authenticating with GCP..."
gcloud auth activate-service-account --key-file ../credentials.json
gcloud config set project udmi-staging

echo "Deploying cloud function..."
gcloud functions deploy udmif_event_handler \
      --runtime=nodejs16 \
      --entry-point=handleUdmiEvent \
      --region=us-central1 \
      --trigger-topic=udmi_target \
      --set-env-vars=MONGO_DATABASE=$MONGO_DATABASE,MONGO_PROTOCOL=$MONGO_PROTOCOL,MONGO_USER=$MONGO_USER,MONGO_PWD=$MONGO_PWD,MONGO_HOST=$MONGO_HOST
