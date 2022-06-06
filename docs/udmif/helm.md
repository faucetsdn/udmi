[**UDMI**](../../../) / [**Docs**](../../) / [**UDMIF**](../) / [**helm.md**](#)

# UDMIF API & Web Build and Deployment

The UDMIF [`API`](../../udmif/api) and [`Web`](../../udmif/web) source code present in udmif folder and helm charts for these two applications are under [`helm`](../../udmif/helm) folder. In this document helps to build UDMIF API & Web and deploy in GKE.

## Pre-requisites
There are some pre-requisites that need to be satisfied in order to build and deploy API and Web applications. 

1. An [existing project on GCP](https://cloud.google.com/resource-manager/docs/creating-managing-projects)
2. Install Docker in local, [setup Docker](https://cloud.google.com/container-registry/docs/quickstart)
3. Install Kubectl in local
4. install Helm in local

## Getting started

#### 1. Build & Publish Docker Image

 API & Web applications contains build script which will build the application, docker image and publish to Google Container registry. Below is the example to run build.sh

 ```
 ./build.sh -p <projectId> -t <image-tag>
 ```
   - projectId - Google projectId
   - tag - Docker image tag

#### 2. Deploy Helm chart in GKE

1. Configure local kube_config with below commands.
   ```
   gcloud container clusters get-credentials udmi-staging-gke --region=us-central1-b
   gcloud container clusters get-credentials udmi-swarm --region=us-central1-f
   ```

2. Deploy helm chart using below commands, GCP_PROJECT_ID need to be updated in values.yaml for [`API`](../../udmif/helm/udmi-api) and [`Web`](../../udmif/helm/udmi-web) helm charts before running below commands.
   ```
   helm upgrade udmi-web  udmi-web --install --debug --namespace udmi --create-namespace --set image.tag=<imag tag> --set env.GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID
   ```
   ```
   helm upgrade udmi-api  udmi-api --install --debug --namespace udmi --create-namespace --set image.tag=<imag tag> --set env.AUTH_CLIENT_ID=$AUTH_CLIENT_ID --set env.CLIENT_IDS=$CLIENT_IDS
   ```
3. Deploy ingress using below commands. Please note that, HOST_NAME need to be updated [`ingress.yaml`](../../udmif/ingress.yaml) before running below command.

   ```
   kubectl apply -f ingress.yaml -n udmi
   ```

