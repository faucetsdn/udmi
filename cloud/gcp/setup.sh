#!/bin/bash -e

echo Using GCP_PROJECT_NAME ${GCP_PROJECT_NAME:?}
echo Using GCP_PROJECT_ID ${GCP_PROJECT_ID:?}
echo Using GCP_PROJECT_REGION ${GCP_PROJECT_REGION:?}
echo Using UDMI_SITE_NAME ${UDMI_SITE_NAME:?}
echo Using UDMI_SITE_REGION ${UDMI_SITE_REGION:?}
echo Using UDMI_SITE_GROUP ${UDMI_SITE_GROUP:?}

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
    -e "s/@UDMI_SITE_GROUP@/${UDMI_SITE_GROUP}/"
