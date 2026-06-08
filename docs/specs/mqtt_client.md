[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [MQTT Client](#)

# MQTT Client

## Connectivity

The client should implement exponential backoff in the event of a loss of connection to the broker
up to a maximum interval period of 120 seconds between connection attempts, and then should continue
connection attempts indefinitely.

## Authentication

When connecting to the MQTT broker using simple username/password authentication (e.g. for local testing or when JWTs are not being used), the credentials are derived as follows:

*   **Username**: The username is formatted as the path to the device, e.g. `/r/{registry}/d/{device}`.
*   **Password**: The password is the first 8 characters of the sha256sum of the private `pkcs8` file.
