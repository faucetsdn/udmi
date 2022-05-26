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
      --set-env-vars=MONGO_DB=udmi,MONGO_PROTOCOL=mongodb+srv,MONGO_USER=UDMIDBUSER,MONGO_PWD=p@sscode123biot,MONGO_HOST=preprod.imvgx.mongodb.net/udmi?retryWrites=true&w=majority