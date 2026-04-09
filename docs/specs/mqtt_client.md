[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [MQTT Client](#)

# MQTT Client

## Connectivity

The client should implement exponential backoff in the event of a loss of connection to the broker
up to a maximum interval period of 120 seconds between connection attempts, and then should continue
connection attempts indefinitely.

## Authentication

When using client certificates for authentication (which is the current typical configuration), password-based authentication is not used. In these cases, the `username` may still be passed as `/r/<registry_id>/d/<device_id>` depending on the backend, but the MQTT broker (e.g., Mosquitto) does not validate passwords.
