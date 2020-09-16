# Pagent Provisioning Agent

`pagent` provides a simple tool for automatic cloud provisioning of devices. Typically,
it involves running the provisioning agent on a local-network web-server that is
configured to connect to an (already created) cloud instance.  There are three main
components to the flow:

* `Device`: IoT device that needs to be provisionined in the cloud.
* `Cloud`: IoT cloud instance that is the ultimate connection endpoint.
* `Agent`: The provisioning agent that receives provision requests and does the needful.

To accomplish there, there are four main steps:

* `Request`: Device requests provisioning from the local agent.
* `Provision`: Local agent provisions the device in the target cloud.
* `Success`: Local agent returns success to the source device.
* `Connect`: Source device then connects to the cloud.

## Sequence Diagram

```
+---------+    +-------+       +-------+
| Device  |    | Cloud |       | Agent |
+---------+    +-------+       +-------+
     |             |               |
     | Request     |               |
     |---------------------------->|
     |             |               |
     |             |     Provision |
     |             |<--------------|
     |             |               |
     |             |       Success |
     |<----------------------------|
     |             |               |
     | Connect     |               |
     |------------>|               |
     |             |               |
```

## Source

https://textart.io/sequence#

```
object Device Cloud Agent
Device->Agent: Request
Agent->Cloud: Provision
Agent->Device: Success
Device->Cloud: Connect
```
