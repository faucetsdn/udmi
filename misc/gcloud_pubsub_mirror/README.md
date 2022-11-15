# GCloud PubSub Mirror

Republishes GCP Pub/Sub messages from one subscription into another project verbatim

**NOTES**
- `messageId` of republished messages **will** differ from the original message
- no error/connection loss handling

# Usage
`python3 mirror.py source_project source_subscription target_project target_topic`