# Basic Nodes for UDMIS setip

# Create a GKE cluster

* Use GCP console
* Add permissions to default service account
* Create node-pool with cloud scopes
  * Can't edit an existing node-pool, but can create a new pool within a cluster

# Misc commands

Things I typed into my command line to get things going (once the cluster was created). In no particular order.
```
gcloud container clusters get-credentials udmis-baseline --region us-central1 --project bos-peringknife-dev
kubectl create secret generic pod-config.json --from-file=etc/gcp_pod.json
kubectl get pods
kubectl apply -f k8s_pod.yaml
kubectl edit pod/udmi-test-pod
kubectl delete pod/udmi-test-pod
kubectl logs udmi-test-pod
kubectl describe pods
kubectl describe pods udmi-test-pod
kubectl config get-contexts
```
