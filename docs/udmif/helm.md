[**UDMI**](../../../) / [**Docs**](../../) / [**UDMIF**](../) / [**helm.md**](#)

# UDMIF API & Web Build and Deployment

The UDMIF [`API`](udmi/udmif/api/build.sh) and [`Web`](udmi/udmif/web/build.sh) source code present in udmif folder and helm charts for these two applications are under [`helm`](udmi/udmif/helm) folder. In this document helps to build UDMIF API & Web and deploy in GKE.

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
   ```
   ```
   gcloud container clusters get-credentials udmi-swarm --region=us-central1-f
   ```

2. Deploy helm chart using below commands
   ```
   helm upgrade udmi-web  udmi-web --install --debug --namespace udmi --create-namespace --set image.tag=<imag tag>
   ```
   ```
   helm upgrade udmi-api  udmi-api --install --debug --namespace udmi --create-namespace --set image.tag=<imag tag>
   ```
3. Deploy ingress using below commands. Please note that, HOST_NAME need to be updated [`ingress.yaml`](udmi/udmif/ingress.yaml) before running below command.

 [`ingress.yaml`](udmi/udmif/ingress.yaml)
```
kubectl apply -f ingress.yaml -n udmi
```

