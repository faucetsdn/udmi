# Smart-Ready Building Technical Primer

Objective: Provide a necessary and sufficient primer for smart-ready building
assembly, to essentially answer the question "What does Smart Ready mean?" and
"How do we know it to be true?" Specifically, it addresses the delta between a
traditional site and smart-ready sites: places where new bleeding edge building
technology is being used.

⁇ Clarification of definition of what this means.
⇒ Things that need to be done.
☐ Thing that can be checked/validated to make sure previous steps are complete/accurate.

## Key Terms & Definitions

### Building Models
There are different levels of "digital maturity" for different buildings. Each
one has a slightly different scope in terms of what it means to be "complete."
For the most part, this document addresses the scope of a Smart Ready building:
-   *Legacy*: It works, but does not meet requirements or guidelines.
-   *Compliant*: Meets basic security and networking requirements.
-   *Smart Ready*: On-prem integrated with the cloud using UDMI & DBO.
-   *Digital Building*: Completely integrated with back-end services.


### Device
Hardware that exists in the building that can change state in a way that can be machine communicated. A potentiometer or a flow switch is a device if it is monitored by a digital system. A commissioning valve or damper with no communication is not a device. 

### Devices Classification
Various adjectives qualify the different flavors of a "device" found in a building. More than one can apply, and each has a specific technical meaning/definition (i.e. they are not subjective).

#### Connectivity
How data is moved around on-prem
-   *Passive*: The device has only passive components (thermistor, dry contact)
-   *Analog*: The device uses an analog connection (1-10V, 4-20mA etc)
-   *Serial*: The device does not have an IP address, rather uses a wired serial connection.
-   *Networked*: The device has its own IP address and presence on the wired or wireless IP network.
-   *Bridge connected*: The device is on an isolated wired or wireless network behind a gateway device that is networked. 

#### Ingestion
How data gets up into the cloud
-   *Direct*: Maintains an authenticated connection directly to Cloud IoT Core.
    -   Must be networked
    -   Has a unique private auth key
-   *Gateway*: A direct device that manages data for other proxied devices.
    -   Bind & attach of proxied devices
    -   Encapsulation anti-pattern
-   *Proxied*: A device that is not direct and is managed through a gateway.
    -   Proxied networked devices, e.g. BACnet/IP
    -   Proxied non-networked devices, e.g. RS485, DALI
-  * Cloud-Cloud*: A device is managed by a third party who provides a cloud connector
    -   Approved third party managed service
    -   UDMI virtual device server connects to third party cloud API

#### Representation
How data is semantically represented
-   *Reporting*: Has an entry in Cloud IoT Core and reports telemetry data.
    -   Reporting connected devices
    -   Reporting proxied devices
-   *Logical*: Has a semantic representation in the site building config.
-   *Virtual*: Logical-but-not-reporting.


## Information Sources

-   *Qualification Summary* - One for each type of networked device
-   *Digital Building Device Register* - Comprehensive list of all devices on a project
-   *Site Model* - Any ingestion device (direct, gateway, or proxy)
-   *Building Config* - Does omit networked devices that don't report

## Cloud IoT Core Definitions
-   Project
    -   Administrative domain (user auth and billing)
    -   Can contain multiple registries (buildings)
-   Registry
    -   Not the same as the Digital Building Device Register
    -   One Registry per building site (one site_model, one building config)
-   Device
    -   May be direct or proxied
    -   Maps to a reporting device
-   Gateway
    -   Proxies for non-authenticating devices
    -   Represented in SiteModel, but maybe not BuildingConfig


## Base Tools & Capabilities

### Device Qualification 
#### Overview
Device qualification is carried out on device types
https://github.com/faucetsdn/daq/blob/master/docs/qualification.md 

⁇ Link to some document in DAQ that describes the overall DAQ flow report.

#### Expectations
⇒ Devices must be officially qualified for use on the project.

#### Verification
☐ Device qualification configuration and reports for the device available.


### System Testing

⁇ Link to documentation on system testing expectations.
⇒ Data flow diagrams and Interoperability Matrix provided.
☐ Interoperability validated against qualified device configurations.

### Managed Network 

⁇ Link to documentation on network scan and configuration expectations
⇒ All devices appropriately registered for network, and asset information provided
☐ ATA validates on-network configuration

### Device Management
#### Overview
All devices which are _smart ready_ are expected to 
⁇ Link to UDMI documentation about devices, site_model, etc…
#### Expectation
⇒ site_model provided and all devices registered into GCP
⇒ devices are setup in a qualified configuration
#### Verificiation 
Registar and device telemetry
☐ registrar and device telemetry validations are clean

### Digital Buildings
#### Overview
⁇ Link to DBO documentation
#### Expectations
⇒ Building Config file provided for site
#### Verification
☐ building config passes DBO validation tools
