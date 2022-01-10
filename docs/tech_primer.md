[**UDMI**](../) / [**Docs**](./) / [Tech Primer](#)

# Smart-Ready Building Technical Primer

This document provides primer for smart-ready building assembly, to essentially
answer the question *"What does Smart Ready mean?"* and *"How do we know it to be
true?"* Specifically, it addresses the delta between a traditional site and
smart-ready sites: places where new bleeding edge building technology is being
used.

# Key Terms & Definitions

## Building Models

There are different levels of "digital maturity" for different buildings. Each
one has a slightly different scope in terms of what it means to be "complete."
For the most part, this document addresses the scope of a Smart Ready building:
-   **Legacy**: It works, but does not meet requirements or guidelines.
-   **Compliant**: Meets basic security and networking requirements.
-   **Smart Ready**: On-prem integrated with the cloud using UDMI & DBO.
-   **Digital Building**: Completely integrated with back-end services.

## Device Classification

Various adjectives qualify the different flavors of a "device" present in the
system. More than one can apply, and each has a specific technical
meaning/definition (i.e. they are not subjective).

### Connectivity

How data is moved around on-prem

-   **Networked**: The device has its own unique IP address on the managed network
-   **Shadowed**: Does not have a managed IP address, for example:
    -  The device connects using a wired serial connection to an IoT gateway
    -  The device is on a shadowed IP network

### Ingestion

How data gets up into the cloud.

-   **Direct**: Maintains an authenticated connection directly to Cloud IoT Core.
    -   Must be networked
    -   Has a unique private auth key
-   **Gateway**: A direct device that manages data for other proxied devices.
    -   Bind & attach of proxied devices
    -   Encapsulation anti-pattern
-   **Proxied**: A device that is not direct and is managed through a gateway.
-   **External**: A device (on-prem or off-prem) consumes data from an external
    source

Each **Direct**, **Gateway**, and **Proxied** device has a unique Device entry in a 
Cloud IoT Core register

### Representation

Device relationships and how they are modeled

-   **Reporting**: Has an entry in Cloud IoT Core and reports telemetry data.
-   **Logical**: Has a semantic representation in the site building config.
-   **Virtual**: Logical-but-not-reporting.

## Information Sources

-   **Qualification Summary** - One for each type of networked device
-   **Digital Building Device Register** - Comprehensive list of all devices on a project
-   **Site Model** - Any ingestion device (direct, gateway, or proxy)
-   **Building Config** - Does omit networked devices that don't report

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
    -   Represented in Site Model, but maybe not Building Config


# Base Tools & Capabilities

## Device Qualification 

[Device qualification](https://github.com/faucetsdn/daq/blob/master/docs/qualification.md)
qualifies device types as meeting a baseline connectivity and security
requirement by performing a series of predefined tests against a device.

### Prerequisites

* Devices are available for qualification
* [DAQ tool](https://github.com/faucetsdn/daq/) set up for use 

### Verification

* Device qualification configuration and reports for the device available.

## Managed Network 

### Prerequisites
* Digital building registry is provided 

### Verification
* ATA validates on-network configuration

## Device Management

All devices which are _smart ready_ are required to support
[UDMI](../../README.md). For guidance on what compliance with the UDMI schema
means, refer to [compliance documentation](compliance.md)

### Prerequisites

* [site model](site_model.md) provided and all devices registered into GCP
* devices are setup in a qualified configuration

### Verification

* [registrar](registrar.md) and [device telemetry validations](validator.md) are clean

## Digital Buildings Ontology

Points and device naming is required to abide by 
[Digital Buildings Ontology (DBO)](https://github.com/google/digitalbuildings)

### Prerequisites

* [Building Config](https://github.com/google/digitalbuildings/blob/master/ontology/docs/building_config.md) file provided for site

### Verification

* building config passes 
[DBO validation tools](https://github.com/google/digitalbuildings/tree/master/tools/validators)
