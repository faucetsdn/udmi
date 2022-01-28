# Connecting Devices to Cloud

## Abstract Framing
```
On-prem             Cloud
        /---*---\
 [D1]---|       |---[D1]
        | stuff |
 [D2]---|       |---[D2]
        \---*---/
```
* _Devices_ (`D1` and `D2` in the diagram)
  * **On-prem**:
  * **Cloud**:
* _Prem-to-Cloud Connectivity_ (ascii `*` in the diagram)
  * **Network**: Basic IP network connectivity.
  * **Transport**: Typically MQTT & GCP IoT Core, but could be something else.

## Connection Models

To achieve the basic abstract end-result above, there's a number of different
models that can be employed, depending on the particular device capabilities.
For the most part, the result _should_ be the same (as indicated in the
abstract case), and if not then it's a red flag for the result.

### Direct

In some cases, devices can speak _direct_ to the cloud. This requires that they
have properly implemented a MQTT driver with proper UDMI message formatting.
This capability is present in many "modern" devices available, and is the
general gold-standard since it minimizes the overall number of system components.

```
On-prem       Cloud
  
 [D1]----*----[D1]

 [D2]----*----[D2]
```

### Adapter

The _adapter_ case, the end device itself doesn't understand MQTT/UDMI, but is
fronted by an _adapter_ (`D1'`) of some kind that translates from whatever the end
device (`D1`) speaks. From UDMI's perspective, there is no sense or awareness
of the connection between `D1` and `D1'` (it could be IP, RS485, or even
[RFC1149](https://datatracker.ietf.org/doc/html/rfc1149)).

```
On-prem          Cloud
  
 [D1][D1']--*----[D1]

 [D2][D2']--*----[D2]
```

### Gateway

For systems that have multiple devices that can be reached by a network of
some kind (IP or otherwise, RFC1149 probably does not apply), an
[_IoT Gateway_](gateway.md) can provide the same result with reduced overhead.
The _gateway_ device (`G1`) exists both on-prem and is represented in the cloud,
but is transparent for end-to-end operation of the system.

```
On-prem              Cloud
  
 [D1]-\           /--[D1]
       [G1]-*-[G1]
 [D2]-/           \--[D2]
```

### Gateway (Singleton)

The degenerate case of a singleton (only one device) system could stil use
a _gateway_ construct; however, this is ultimately less elegant since there is extra
configuration and overhead required. In this case, the _adapter_ model
is preferred as it's overall more clean (but a _gateway_ would still be functionally
correct). Many devices can be configured to be either an _adapter_ or a _gateway_,
so it might be the same hardware but different setup.

```
On-prem              Cloud
  
 [D1]--[G1]-*-[G1]---[D1]
```

### Aggregator

The _aggregator_ model is essentially a "poor-man's gateway" in that it has the same
on-prem architecture but is treated differently in the cloud. In this case, all the
data points from all the devices are aggregated together into one payload, using special
point-name encodings to differentiate points on the receiving end. The fundamental problem
with this architecture is that it will not be able to support some back-end UDMI features
that rely on devices being mapped as logical entities on the cloud end. This case is
sometimes (unfortunately) required by limitations in implementations, but is not considered
UDMI compliant.

```
On-prem              Cloud
  
 [D1]-\
       [A1]----------[A1]
 [D2]-/
```
