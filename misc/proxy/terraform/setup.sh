PROJECT_ID=@GCP_PROJECT_ID@
SA_NAME=terraform
KEYPATH_LOCATION=auth # HARDCODED
STATE_BUCKET=@GCP_PROJECT_ID@-terraform-state-bucket
DOMAIN=@DOMAIN@

LOCATION=us-central1-f

SERVICE_ACCOUNT=$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com 

gcloud config set project $PROJECT_ID
gcloud config set compute/zone $LOCATION
gcloud auth configure-docker

gcloud services enable  serviceusage.googleapis.com \
                    cloudresourcemanager.googleapis.com \
                    compute.googleapis.com \
                    sourcerepo.googleapis.com \
                    cloudbuild.googleapis.com \
                    cloudfunctions.googleapis.com \
                    cloudiot.googleapis.com \
                    cloudidentity.googleapis.com \
                    dns.googleapis.com \
                    sqladmin.googleapis.com \
                    vpcaccess.googleapis.com \
                    container.googleapis.com 

gcloud iam service-accounts create $SA_NAME --display-name="Terraform Service Account"
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT" --role="roles/owner" 

mkdir -p $KEYPATH_LOCATION
gcloud iam service-accounts keys create $KEYPATH_LOCATION/credentials.json --iam-account=$SERVICE_ACCOUNT

gcloud storage buckets create gs://$STATE_BUCKET --project=$PROJECT_ID --default-storage-class=STANDARD --location=us-central1 


