# PubSub Mirror

Republishes GCP Pub/Sub messages from one subscription into another project verbatim

**NOTES**
- `messageId` of republished messages **will** differ from the original message
- no error/connection loss handling

# Usage
`python3 mirror.py source_project source_subscription target_project target_topic`

To capture file traces, try:
```
while true; do venv/bin/python3 misc/gcloud_pubsub_mirror/mirror.py ${project_id} ${subscription_id} // trace/; done
```

To playback file traces, use:
```
venv/bin/python3 misc/gcloud_pubsub_mirror/mirror.py // trace/ ${project_id} ${topic_id}
```
