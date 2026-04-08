[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [MQTT Client](#)

# MQTT Client

## Connectivity

The client should implement exponential backoff in the event of a loss of connection to the broker
up to a maximum interval period of 120 seconds between connection attempts, and then should continue
connection attempts indefinitely.

## Authentication

When using password-based MQTT authentication:
* The `username` is formatted as `/r/<registry_id>/d/<device_id>` (e.g., `/r/ZZ-TRI-FECTA/d/AHU-1`).
* The `password` is the first 8 characters of the sha256 hash of the private pkcs8 file.
