#!/bin/bash -e

CRED_FILE=auth/credentials.json

echo export GCP_PROJECT_NAME=${GCP_PROJECT_NAME:?}
echo export GCP_PROJECT_ID=${GCP_PROJECT_ID:?}
echo export GCP_PROJECT_REGION=${GCP_PROJECT_REGION:?}
echo export GCP_PROJECT_GROUP=${GCP_PROJECT_GROUP:?}
echo export UDMI_SITE_NAME=${UDMI_SITE_NAME:?}
echo export UDMI_SITE_REGION=${UDMI_SITE_REGION:?}
echo export UDMI_SITE_GROUP=${UDMI_SITE_GROUP:?}
echo export GCP_PROJECT_CREDS=${GCP_BASE64_CREDS:?}

if [[ -f $CRED_FILE ]]; then
    echo Cowardly refusing to overwrite existing $CRED_FILE
    false
else
    base64 -d <<< "${GCP_PROJECT_CREDS}" > $CRED_FILE
fi

sed -E < main.tf.template > main.tf \
    -e "s/@GCP_PROJECT_ID@/${GCP_PROJECT_ID}/"

sed -E < udmi-sites.tf.template > udmi-sites.tf \
    -e "s/@UDMI_SITE_NAME@/${UDMI_SITE_NAME}/" \
    -e "s/@UDMI_SITE_REGION@/${UDMI_SITE_REGION}/" \
    -e "s/@UDMI_SITE_GROUP@/${UDMI_SITE_GROUP}/"

sed -E < terraform.tfvars.template > terraform.tfvars \
    -e "s/@GCP_PROJECT_NAME@/${GCP_PROJECT_NAME}/g" \
    -e "s/@GCP_PROJECT_ID@/${GCP_PROJECT_ID}/" \
    -e "s/@GCP_PROJECT_REGION@/${GCP_PROJECT_REGION}/" \
    -e "s/@GCP_PROJECT_GROUP@/${GCP_RPOJECT_GROUP}/"
