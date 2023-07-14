# Basic Nodes for UDMIS setip

# Create a GKE cluster

* Use GCP console
* Do not use "autopilot", instead use "standard" mode (not default)
* Create node-pool with cloud scopes to "Enable all Cloud APIs"
  * There's an option when you're creating it to set the node-pool security options
  * Can't update an existing node-pool, but can create a new one and delete old one
* Add permissions to default service account

# Misc commands

Things I typed into my command line to get things going (once the cluster was created). In no particular order.
```
gcloud config set project bos-test-dev
gcloud container clusters get-credentials XXXXXXX --region us-central1 --project bos-test-dev
kubectl config get-contexts
kubectl config use-context XXX_bos-test-dev_XXX
kubectl create secret generic pod-config.json --from-file=var/prod_pod.json
kubectl get pods
kubectl apply -f k8s_pod.yaml
kubectl edit pod/udmi-test-pod
kubectl delete pod/udmi-test-pod
kubectl logs udmi-test-pod
kubectl describe pods
kubectl describe pods udmi-test-pod
kubectl exec -ti udmi-test-pod -- bash
```
