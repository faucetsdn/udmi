[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./)
/ [MQTT Client](#)

# MQTT Client

## Connectivity

The client should implement exponential backoff in the event of a loss of connection up to a maximum
interval period between connection attempts, and then should continue connection attempts
indefinitely 