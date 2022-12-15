 #!/bin/bash line

echo "Executing build..."
source ./build.sh
cd dist

echo "Deploying cloud function..."
gcloud functions deploy udmif_event_handler \
      --runtime=nodejs16 \
      --entry-point=handleUdmiEvent \
      --region=$REGION \
      --trigger-topic=udmi_target \
      --set-env-vars=POSTGRESQL_INSTANCE_HOST=$POSTGRESQL_INSTANCE_HOST,POSTGRESQL_PORT=$POSTGRESQL_PORT,POSTGRESQL_USER=$POSTGRESQL_USER,POSTGRESQL_PASSWORD=$POSTGRESQL_PASSWORD,POSTGRESQL_DATABASE=$POSTGRESQL_DATABASE
