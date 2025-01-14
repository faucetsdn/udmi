# Automated Sequencer Reporting

Automated sequencer running and reporting using GKE, GCS and Pub/Sub

- `sequencer` is a utility which:
    - clones the UDMI repository
    - builds sequencer
    - runs sequencer against a given device
    - uploads results to GCS
    - publishes a Pub/Sub message to 
- `reporter` is a utility which:
    - receieves a Pub/Sub message
    - downloads results from GCS
    - generates a report
    - uploads report to GCS

## Example Kubernetes Deployments

### Reporter

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: reporter
  namespace: sequencer
  labels:
    app: reporter
spec:
  replicas: 1
  selector:
    matchLabels:
      app: reporter
  template:
    metadata:
      labels:
        app: reporter
    spec:
      containers:
      - name: reporter
        image: reporter
        env:
        - name: PUBSUB
          value: "udmi-sequencer-notification"
        - name: GCS_BUCKET
          value: "gcs-bucket-id"
        - name: PROJECT_ID
          value: "gcp-project-id"
```

### Sequencer

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: sequencer-ddc-8
  namespace: sequencer
spec:
  concurrencyPolicy: Forbid
  schedule: "36 1 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: sequencer
            image: sequencer
            resources:
              requests:
                cpu: 1
                memory: 1000Mi
            volumeMounts:
            - name: tmp
              mountPath: /tmp
            env:
            - name: PROJECT_ID
              value: "gcp-project-id"
            - name: SEQUENCER_PROJECT_ID
              value: "//gref/gcp-project-id+ddc-8"
            - name: SITE_PROJECT
              value: "gcp-project-id"
            - name: SITE_NAME_REPO
              value: "UK-LON-GLAB"
            - name: BRANCH
              value: "main"
            - name: DEVICE_ID
              value: "DDC-8"
            - name: GCS_BUCKET
              value: "gcs-bucket-id"
            - name: SITE_MODEL_SUBDIR
              value: "udmi"
            - name: PUBSUB
              value: "udmi-sequencer-notification"
            - name: PUBSUB_PROJECT
              value: "gcp-project-id"
            - name: UDMI_BRANCH
              valueFrom:
                configMapKeyRef:
                  name: data
                  key: udmi_branch
            - name: UDMI_REPO
              valueFrom:
                configMapKeyRef:
                  name: data
                  key: udmi_repo
          restartPolicy: Never
          volumes:
          - name: tmp
            emptyDir:
              medium: Memory

```