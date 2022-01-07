[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [MQTT Client](#)

# MQTT Client

## Connectivity

The client should implement exponential backoff in the event of a loss of connection to the broker
up to a maximum interval period of 120 seconds between connection attempts, and then should continue
connection attempts indefinitely.