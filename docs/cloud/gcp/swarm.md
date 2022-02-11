[**UDMI**](../../../) / [**Docs**](../../) / [**Cloud**](../) / [**GCP**](./) / [Swarm](#)

# UDMI Swarm Pubber Cluster

* PubSub topic/subscription (e.g. swarm-feed)
  * Manually create in GCP
* GKE cluster to run pubber container
  * files in pubber/etc/
  * Cluster setup requires explicit PubSub API permission
* Cloud Run to run swarm container
  * files in validator/etc/
* Cloud Scheduler to trigger cloud run
  * Manually setup to run every hour
* container build/deploy script
  * bin/deploy -- covers both pubber and validator
