gcloud auth activate-service-account --key-file credentials.json
gcloud config set project udmi-staging
gcloud functions deploy transform-event-handler \
      --runtime=nodejs16 \
      --entry-point=handleUDMIEvent \
      --region=us-central1 \
      --source=dist \
      --trigger-topic=udmi_target \
      --set-env-vars=[MONGO_PROTOCOL=mongodb+srv,MONGO_USER=UDMIDBUSER,MONGO_PWD=p@sscode123biot,MONGO_HOST=preprod.imvgx.mongodb.net/udmi?retryWrites=true&w=majority,MONGO_DB=udmi]