# Basic Nodes for UDMIS setip

# Create a GKE cluster

* Go to `Kubernetes Engine` GCP console page
  * (Enable Kubernetes Engine API)
* Create a cluster
  * Start creating a _standard_ cluster, not an _autopilot_ cluster.
  * Under `node pools`, `default-pool`, `security`, set `Access scopes` to `Allow full access to all Cloud APIs`
  * Complete creating the cluster
  * If you didn't set the node pool security policy, you either need to recreate the cluster or the node pool.
* Wait for the cluster to be created (~15min)
* Click three-dot menu for the cluster and select `connect`
  * Copy/paste/execute the Command-line access option on your local dev machine
* Using GCP console `IAM` page
  * Add permissions (viewer?) to the `Compute Engine default service account`

# Misc commands

Things I typed into my command line to get things going (once the cluster was created). In no particular order.
```
gcloud config set project bos-test-dev
gcloud container clusters get-credentials XXXXXXX --region us-central1 --project bos-test-dev
kubectl config get-contexts
kubectl config use-context XXX_bos-test-dev_XXX
kubectl config set-context --current --namespace=udmis
kubectl create secret generic clearblade.json --from-file=clearblade.json=$HOME/creds/udmi-external-credentials.json
kubectl create secret generic k8s-info --from-literal=context=$(kubectl config current-context)
kubectl apply -f tmp/k8s_config.yaml
kubectl apply -f etc/k8s_broker.yaml
kubectl get pods
kubectl delete pod/udmis-test-pod
kubectl logs udmis-test-pod
kubectl describe pods
kubectl describe pods udmis-test-pod
kubectl exec -ti udmis-test-pod -- bash
```
