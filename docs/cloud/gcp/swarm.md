[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [Swarm](#)

# UDMI Swarm Pubber Cluster

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
  * Create cluster
  * Get kubectl certs:
  `gcloud --project=${GCP_PROJECT} container clusters --zone=us-central1-c get-credentials pubber-swarm`

## Deploy Cluster

  * Make sure to target the right cluster, `kubectl config current-context`
  * Deploy workload `envsubst < pubber/etc/deployment.yaml | kubectl apply -f -`
  
## Container build/deploy script

```
udmi$ bin/deploy ${GCP_PROJECT}
```

This will:
  * Build two docker images { _pubber_ and _validator_ }
  * Upload them to the project container registry

## PubSub topics/subscriptions

* PubSub topic/subscription (e.g. swarm-feed)
  * Manually create in GCP
* GKE cluster to run pubber container
  * files in pubber/etc/
* Cloud Run to run swarm container
  * files in validator/etc/
* Cloud Scheduler to trigger cloud run
  * Manually setup to run every hour
