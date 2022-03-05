[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [Swarm](#)

# UDMI Swarm Pubber Cluster

  * k8s cluster running a swarm of _pubber_ nodes that simulate IoT Core client devices.
  * cloud run function triggered by cron to manage swarm worker pool.
  * glue PubSub topic/subscriptions to distribute work to nodes.

## Setup IoT Core Registry

  * Create site registry
  * Run registrar

## Setup local admin system

  * gcloud, kubectl, docker
  * get latest udmi repo `git clone https://github.com/faucetsdn/udmi.git`
  * All commands assume running in the root directory of the cloned repo

## Create/identify GCP project

  * Set project location if/as necessary for your organization
  * Docs assume GCP_PROJECT env varaible is set appropriately, e.g.
  `export GCP_PROJECT=udmi-swarm-example`

## Create GKE Cluster

  * Enable API
  * "GKE Standard" cluster (not "Autopilot")
  * Name appropriately (e.g. "pubber-swarm")
  * Enable Pub/Sub Access Scope
    * NODE POOLS > default-pool > security
    * Access scope "set access for each API"
      * Set "Cloud Pub/Sub" to "Enabled"
      * Maybe also set "Cloud Platform" to "Enabled" -- not sure if this is required
  * Create cluster
  * Get kubectl certs:
  `gcloud --project=${GCP_PROJECT} container clusters --zone=us-central1-c get-credentials pubber-swarm`

  * Maybe need to enable access to the container registry?  I did this, but not sure it's required:
`gsutil iam ch serviceAccount:943284686802-compute@developer.gserviceaccount.com:roles/storage.objectViewer gs://us.artifacts.udmi-swam-example.appspot.com/`

## Container build/deploy script

```
udmi$ bin/deploy ${GCP_PROJECT}
```

This will:
  * Build two docker images { _pubber_ and _validator_ }
  * Upload them to the project container registry

## Deploy Cluster

  * Make sure to target the right cluster, `kubectl config current-context`
  * Deploy workload `envsubst < pubber/etc/deployment.yaml | kubectl apply -f -`

## Configure PubSub

  * Create a new topic called `swarm-feed`
  * Create a simple subscription also called `swarm-feed`
  * Set the message retention on the subscription to 10 minutes (minimum)
  * Add the default compute engine service account (something like `XXXXXXXXX-compute@developer.gserviceaccount.com`)
  as a "PubSub Subscriber" to the subscription.

## Create CloudRun Trigger

  * Create a new cloud run service
  * Image will be something like `us.gcr.io/udmi-swarm-example/validator` (except with the right project instead of `udmi-swarm-example`)
  * Change maximum number of instances to 1
  * Set Authentication: Require authentication
  * Select "Second generation execution environment"
  * Note the service URL, something like `https://validator-t22jzfa4pq-de.a.run.app`

## Create Cloud Scheduled Cron Job

  * Create a new job
  * Name `swarm`
  * Frequency of every 15 minutes with `*/15 * * * *`
  * Target type "HTTP"
  * URL is something like `https://validator-t22jzfa4pq-de.a.run.app/?site=sg-sin-mbc2&topic=swarm-feed`
    * The first bit is the taken from the Cloud Run service
    * The site (`sg-sin-mbc2`) defines the set of devices to simulate
    * Topic should point at the created PubSub topic.
  * HTTP method "GET"
  * Auth header "Add OIDC token"
  * Default Compute Engine Service account
  * Audience is the Cloud Run service URL
  * Go to IAM and add Cloud Run Invoker to roles for the Default Compute Engine service account

## Things to check

  * Cloud Run logs to make sure it's getting triggered
  * PubSub topic to see it's receiving info from the Cloud Run
  * PubSub subscription to see if the swarm pubber instances are pulling from the subscription
  * Check k8s pod logs to see if they're getting targets and connecting properly
  * IoT Core to see if it's receiving data
