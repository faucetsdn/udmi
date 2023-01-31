[**UDMI**](../) / [**UDMIF**](./) / [Deploy](#)

# UDMIF API & Web deployment to Kubernetes

[Helm](https://helm.sh/) is a popular package manager for Kubernetes that is leveraged to deploy the UDMIF application to the Kubernetes cluster that is created as part of the terraform process. The UDMIF [API](./api) and [Web](./web) source code needs to be built locally and then deployed using the appropriate [helm charts](./helm) and scripts.

## Prerequisites
There are some prerequisites that need to be satisfied in order to build the docker images for those projects and deploy them to Kubernetes.

1. An [existing terraformed project in GCP](../docs/cloud/gcp/terraform.md)
2. Docker locally installed
3. [Kubectl locally installed](https://kubernetes.io/docs/tasks/tools/)
4. [Helm locally installed](https://helm.sh/docs/intro/install/)

## Getting started

### 1. Build & Publish the Docker images

The [API](./api) and [Web](./web) folders each contain a docker build script that generates the docker image for the application and publish it to GCR. 
 
> NOTE: Make sure your gcloud CLI is authenticated against the project you will be using in GCP and properly configured.
```
  gcloud config set project <project_id>
  gcloud config set compute/zone <region>
  gcloud auth login
  gcloud auth configure-docker
```
 
 Here is an example on how to run the docker build script:

```
# ./buildDocker.sh -p <projectId> -t <image-tag>
./buildDocker.sh -p my-project-id -t latest
```

### 2. Deploy the services in GKE using the Helm charts

Helm is used to template and deploy the Web and API UDMIF services in GKE. You can get more information about Helm right [here.](https://helm.sh/)

#### Configure kube_config.

You need to configure your project ID, compute zone and GKE cluster credentials before you can deploy pods in it. 

   Example:
   ```
   gcloud container clusters get-credentials <cluster_name>
   ```

#### Deploy charts
Deploy the helm charts using helm upgrade commands for the appropriate image tag you built and deployed above. You need to update the GCP_PROJECT_ID in the **repository:** section and provide values for all variables in the **env:** section for [`API`](./helm/udmi-api) and [`Web`](./helm/udmi-web) helm charts before running the commands. Once the values.yaml file has been updated, you can install or update the chart with the following commands.

   You need to be in the  [helm](./helm) directory before issuing the commands. Image Tag should be a tag that was pushed to GCR in the docker build phase. 

   udmi-web:
   ```
   helm upgrade --install --debug --namespace udmi --create-namespace --set image.tag=<image tag> udmi-web ./udmi-web
   ```

   udmi-api:
   ```
   helm upgrade --install --debug --namespace udmi --create-namespace --set image.tag=<image tag>  udmi-api ./udmi-api
   ```

#### Deploy ingress
Deploy the ingress using the following command. Please note that you must update the HOST_NAME in [`ingress.yaml`](./ingress.yaml) before running the command. that hostname should be using the full domain name that you created in terraform. For example: dashboard.udmi.mydomain.com. You should ensure that you have proper DNS records in place so that this domain is resolvable over the internet.

   ```
   kubectl apply -f ingress.yaml --namespace udmi
   ```

#### Deploy `eventHandler` cloud function.

The event handler cloud function is deployed with a script that leverages gCloud to create the function. The script is named [deploy.sh] (event-handler/deploy.sh) located in the event-handler folder. Please ensure you set the required environment variables before running the script. You also need to ensure your credential.json file is available at the proper [location](../cloud/gcp/auth/) that your project ID is set.

example:
```
#run from the event-handler folder.
export REGION=us-central1
export MONGO_DATABASE=udmi
export MONGO_PROTOCOL=mongodb+srv
export MONGO_USER=someUser
export MONGO_PWD=somePassword
export MONGO_HOST="something.somewhere.mongodb.net/?retryWrites=true&w=majority&authSource=admin"

#Authentication with GCP"
gcloud config set project udmi
gcloud auth activate-service-account --key-file ../../cloud/gcp/auth/credentials.json

./deploy.sh
```
