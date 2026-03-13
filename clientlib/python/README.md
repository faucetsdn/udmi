# A Python Library for UDMI

This project is a high-level, extensible Python library designed to simplify the
development of applications for the **Universal Device Management Interface (UDMI)**. 
It provides a clean, Pythonic abstraction over the core UDMI
specification, lowering the barrier to entry for developers and device
manufacturers seeking to create UDMI-compliant IoT solutions.

## Key Features

* **Strict Schema Adherence**: Ensures 1:1 compliance with the UDMI data model
  by using Python dataclasses that are auto-generated directly from the official
  UDMI JSON schemas.

* **Abstracted Complexity**: Handles core device functionalities like MQTT
  client communication, authentication (including certificate management), and
  the primary config/state message loop.

* **Extensible by Design**: Uses abstract base classes to allow for easy
  customization and support for future protocols or features.

The library acts as a reference implementation for demonstrating core
functionalities like connectivity, telemetry, and message handling.

---

## Local Setup & Usage Examples

To install and build the library locally use the build script.

```shell
${UDMI_ROOT}/clientlib/python/bin/build
```

You can find a few samples demonstrating how to connect a device using different
authentication methods as well as other features of the library in
`$UDMI/ROOT/clientlib/python/samples`.

---

## Comprehensive Feature List

## 1\. Smart Connectivity & Authentication

**Status:** Available

* **Unified Device Factory:** A single entry point (`create_device`) that instantiates the client, dispatcher, and necessary managers based on the provided configuration.  
* **Auto-Auth Detection:** Logic to automatically select between Mutual TLS (mTLS), No Auth (Anonymous mode), Basic Auth (Username/Password), or RS256 JWT authentication without explicit code changes.  
* **JIT Credential Generation:** A built-in `CredentialManager` detects missing keys and generates RSA/ECC key pairs and self-signed certificates on the fly ("Zero-Config" mTLS/JWT).  
* **Connection Robustness:** Automatic reconnection handling using `paho-mqtt` with configurable exponential backoff parameters to survive network instability.

**Sample Usage: [`simple_connect.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/connectivity/simple_connect.py)**  
 

```py
from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# The library automatically detects auth type (or lack there) of from this 
# config object
config = EndpointConfiguration.from_dict({
    "client_id": "projects/my-project/locations/us-central1/registries/reg/devices/AHU-1",
    "hostname": "mqtt.bos.goog",
    "port": 8883,
    "auth_provider": {"jwt": {"audience": "my-project"}}
})

# Automatic wiring of client, dispatcher, and managers.
# If 'rsa_private.pem' is missing, the factory generates it automatically.
device = create_device(config, key_file="rsa_private.pem")
device.run()
```

## 2\. Device State Management

**Status:** Available

* **Automated Config Loop:** Automatically handles incoming `config` messages, updates the `system.last_config` timestamp, and publishes the acknowledged `state`.  
* **Static State Injection:** The `SystemManager` accepts a typed `SystemState` object during initialization, allowing manufacturers to inject static identity fields (Serial Number, Firmware Version, Model) without complex subclassing.  
* **Config Sync Latch:** Logic ensures the device does not publish its initial state until a valid Config message is received or a timeout expires, preventing state thrashing on startup.  
* **Atomic Persistence:** Critical state data (such as `restart_count` and active endpoint configurations) is saved atomically to disk to prevent data corruption and ensure continuity after power loss.

**Sample Usage: [`state_injection.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/system/state_injection.py)**

```py
from udmi.core.managers import SystemManager
from udmi.schema import SystemState, StateSystemHardware

# Define static device identity once
static_info = SystemState(
    hardware=StateSystemHardware(make="Delta", model="Red5"),
    serial_no="SN-12345",
    software={"firmware": "v2.0"}
)

# Inject into the manager using the factory; the library handles the rest
device = create_device(system_state=static_info,
   persistence_path=/path/to/persistence.json)

```

## 

## 3\. Telemetry & Observability

**Status:** Available

* **Pointset Management:** A dedicated `PointsetManager` handles the `pointset` configuration block, manages sensor values, and runs a background loop to publish `PointsetEvents` at the configured sample rate.  
* **Unified Logging:** A `UDMIMqttLogHandler` integrates with Python's standard `logging` module. It captures application logs (INFO, WARN, ERROR), formats them as UDMI `SystemEvents`, and publishes them to the cloud for remote diagnostics.  
* **System Metrics:** The `SystemManager` automatically collects and reports system health metrics (e.g., RAM usage) in the `system` event stream.

**Sample Usage:**   
[**`telemetry_basic.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/pointset/telemetry_basic.py)**

```py
# Update the PointsetManager (Internal Cache)
pointset_manager.set_point_value("supply_temp", 22.5)

# The background thread automatically publishes this at the configured 'sample_rate_sec'. 
```

[**`logging_integration.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/events/logging_integration.py)**

```py
import logging
from udmi.core.logging.mqtt_handler import UDMIMqttLogHandler

# Attach UDMI handler to standard Python logger
mqtt_handler = UDMIMqttLogHandler(system_manager)
logger = logging.getLogger("my_app")
logger.addHandler(mqtt_handler)

# This log appears in the cloud dashboard automatically as a SystemEvent
logger.warning("High CPU usage detected!")
```

## 

## 4\. Pointset Control (Writeback & Provisioning)

**Status:** Available

* **Writeback (Actuation):** Handles `set_value` commands from the cloud (e.g., changing a setpoint). The library parses the command and triggers a registered user callback to execute the hardware change.  
* **Dynamic Provisioning:** Seamlessly merges points defined in the initial firmware model with new points provisioned dynamically via the Cloud Configuration.  
* **Polling Support:** Supports a "pull" model via set\_poll\_callback for just-in-time data retrieval.

**Sample Usage:**   
[**`point_writeback.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/pointset/point_writeback.py)**, 

```py
def on_writeback(point_name: str, value: Any):
    print(f"Actuating {point_name} to {value}...")
    # Hardware logic here...

# Register the handler
pointset_manager.set_writeback_handler(on_writeback)
```

[**`pointset_dynamic_configuration.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/pointset/pointset_dynamic_provisioning.py)**, 

[**`telemetry_poll_callback.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/pointset/telemetry_poll_callback.py)**

```py
pointset_manager.set_poll_callback(my_sensor_poll)
```

## 

## 5\. Over-The-Air (OTA) Updates

**Status:** Available

* **Generic Blob Fetching:** Utilities to fetch data from HTTP(S) URLs or inline Data URIs (`data:base64...`).  
* **Automatic Verification:** The library enforces **SHA256 hash verification** on all downloaded blobs before passing them to the application logic.  
* **Two-Stage Workflow:** Supports a "Process" \-\> "State Flush" \-\> "Post-Process" pipeline to safely handle self-updates and restarts without leaving the cloud in an unknown state.

**Sample Usage: [`ota_update_workflow.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/system/ota_update_workflow.py)**

```py
sys_manager.register_blob_handler(
    "ota_module_loader",
    process=process_firmware,      # Writes file to disk
    post_process=restart_device    # Restarts the app
)
```

## 

## 6\. Gateway, Proxy & Discovery

**Status:** Available

* **Proxy Lifecycle:** Handles attach and detach messages for sub-devices.  
* **Routed Configuration:** Config messages and Commands targeted at specific proxy IDs are automatically routed to registered handlers.  
* **Discovery Manager:** Implementation of the discovery block to support active scanning using registered `FamilyProvider` drivers and reporting `DiscoveryEvents`.

**Sample Usage:**   
[**`gateway_proxy.py`**](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/gateway/gateway_proxy.py),**  
[**`discovery_scan.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/gateway/discovery_scan.py)**

```py
# Triggered by cloud 'discovery' command
class MyBacnetProvider(FamilyProvider):
    def start_scan(self, config, publish):
        # Scan network...
        publish(device_id, DiscoveryEvents(...))
```

## 

## 7\. Others: Reliability, Localnet & Lifecycle Management

**Status:** Available

* **State Throttling:** "Dirty bit" logic triggers state updates immediately upon change, subject to a minimum time interval (STATE\_THROTTLE\_SEC) to prevent broker spamming.  
* **Endpoint Redirection:**  
  * **Sample usage: [`endpoint_redirection.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/advanced/endpoint_redirection.py)**  
* **Key Rotation:** The `SystemManager` handles the `rotate_key` command and also has a public API `rotate_key`, triggering the `CredentialManager` to generate new keys, backup old ones, and invoke a callback for cloud registration.  
  * **Sample usage: [`key_rotation.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/advanced/key_rotation.py)**  
* **Localnet Manager:** Handles the localnet configuration block to validate address families (e.g., mapping generic device IDs to physical BACnet addresses) using registered providers.  
  * **Sample usage: [`localnet_routing.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/localnet/localnet_routing.py)**  
* **Lifecycle Commands:** Maps UDMI commands (reboot, shutdown) to registered callbacks or specific process exit codes.  
  * **Sample usage: [`lifecycle_commands.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/system/lifecycle_commands.py)**  
* **Custom Persistence Backend:**  
  * **Sample usage: [`custom_persistence_backend.py`](https://github.com/faucetsdn/udmi/tree/master/clientlib/python/samples/advanced/custom_persistence_backend.py)**

## 
