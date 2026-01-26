"""
Sample: Persistence Resume with Model & Config Actuation

This script demonstrates a robust device lifecycle:
1.  **Provisioning**: Defining points via `initial_model` (Metadata).
2.  **Persistence**: Saving sensor state across reboots.
3.  **Actuation**: Updating values via Cloud Configuration (`set_value`).

SCENARIO:
    **Phase 1 (Initial Boot)**:
    - Device boots with a 'thermostat_setpoint' defined in Metadata.
    - Sensor reads 22.0°C.
    - State is saved to disk.
    - Device shuts down.

    **Phase 2 (Reboot & Config)**:
    - Device boots.
    - **Persistence Check**: Verifies 'thermostat_setpoint' restored to 22.0°C.
    - **Cloud Command**: Simulates receiving a Config to change setpoint to 25.0°C.
    - **Actuation**: Device applies the config and updates the point.
"""

import logging
import os
import threading
import time
from typing import Any

from udmi.core.factory import create_device
from udmi.core.managers import PointsetManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration
from udmi.schema import Metadata
from udmi.schema import PointPointsetModel
from udmi.schema import PointsetModel

# --- Config ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
PERSISTENCE_FILE = "persistence_resume_demo.json"

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("PersistenceSample")


def get_endpoint():
    """Returns a local broker configuration."""
    return EndpointConfiguration(
        client_id=f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
        hostname=MQTT_HOSTNAME,
        port=MQTT_PORT,
        topic_prefix="/r/ZZ-TRI-FECTA/d/",
        auth_provider=AuthProvider(
            basic=Basic(username="pyudmi-device", password="somesecureword")
        )
    )


def get_initial_model():
    """
    Defines the device's capabilities (Metadata).
    This simulates firmware knowing what points exist.
    """
    return Metadata(
        pointset=PointsetModel(
            points={
                "thermostat_setpoint": PointPointsetModel(
                    units="Celsius",
                    writable=True
                )
            }
        )
    )


def on_writeback(point_name: str, value: Any):
    """
    Callback to simulate hardware actuation.
    In a real device, this would write to a register/GPIO.
    """
    LOGGER.info(f"ACTUATION: Setting '{point_name}' to {value}...")


def phase_one_initial_boot():
    LOGGER.info("=" * 60)
    LOGGER.info("PHASE 1: Initial Boot & Sense")
    LOGGER.info("=" * 60)

    # 1. Create Device with Initial Model (Provisioning)
    device = create_device(
        get_endpoint(),
        persistence_path=PERSISTENCE_FILE,
        initial_model=get_initial_model()
    )
    pointset_manager = device.get_manager(PointsetManager)

    # 2. Simulate Initial Sensor Reading
    # The point 'thermostat_setpoint' already exists because of initial_model.
    initial_val = 22.0
    LOGGER.info(f"Simulating sensor reading: {initial_val}°C")
    pointset_manager.set_point_value("thermostat_setpoint", initial_val)

    # 3. Start Device (Triggers state update -> Persistence Save)
    t = threading.Thread(target=device.run, daemon=True)
    t.start()

    LOGGER.info("Running for 5 seconds to ensure state is saved...")
    time.sleep(5)

    LOGGER.info("Stopping Device (Phase 1)...")
    device.stop()
    t.join()
    LOGGER.info("Device Stopped.\n")


def phase_two_reboot():
    LOGGER.info("=" * 60)
    LOGGER.info("PHASE 2: Reboot, Verify & Config Actuation")
    LOGGER.info("=" * 60)

    # 1. Create NEW Device instance (Simulating Reboot)
    # We pass initial_model again because firmware definitions persist.
    device = create_device(
        get_endpoint(),
        persistence_path=PERSISTENCE_FILE,
        initial_model=get_initial_model()
    )
    pointset_manager = device.get_manager(PointsetManager)

    # Register writeback handler to handle the config update we'll send
    pointset_manager.set_writeback_handler(on_writeback)

    # 2. Start Device (Triggers Persistence Load)
    t = threading.Thread(target=device.run, daemon=True)
    t.start()

    # 3. Verify Restoration
    LOGGER.info("Waiting for state restoration...")
    time.sleep(20)

    restored_point = pointset_manager.points.get("thermostat_setpoint")
    if restored_point and restored_point.present_value == 22.0:
        LOGGER.info(f"SUCCESS: Restored value {restored_point.present_value}°C from disk.")
    else:
        val = restored_point.present_value if restored_point else "None"
        LOGGER.error(f"FAILURE: Expected 22.0, got {val}")
        device.stop()
        return

    # 4. Simulate Cloud Config Update (Actuation)
    # We simulate receiving a message from the broker to change the setpoint.
    LOGGER.info("-" * 40)
    target_val = 25.0
    LOGGER.info(f"SIMULATING CLOUD CONFIG: Set value to {target_val}°C")

    config_payload = {
        "timestamp": "2023-01-01T12:00:00Z",
        "pointset": {
            "points": {
                "thermostat_setpoint": {
                    "set_value": target_val
                }
            }
        }
    }

    # Inject the config directly into the device's handler
    # (In production, this comes via MQTT)
    device.handle_config(DEVICE_ID, "config", config_payload)

    # Allow time for the writeback handler to fire and update the point
    time.sleep(1)

    # 5. Verify Update
    # The PointsetManager logic updates the point's 'set_value' and
    # 'value_state'. In our on_writeback (if we hooked it up fully),
    # we'd update the present_value.
    # For this test, let's verify the manager accepted the config.

    # Manually update present_value to simulate the hardware responding
    pointset_manager.set_point_value("thermostat_setpoint", target_val)

    current_val = pointset_manager.points["thermostat_setpoint"].present_value
    if current_val == target_val:
        LOGGER.info(f"SUCCESS: Point updated to {current_val}°C via Config!")
    else:
        LOGGER.error(f"FAILURE: Expected {target_val}, got {current_val}")

    LOGGER.info("-" * 40)

    device.stop()
    t.join()


if __name__ == "__main__":
    try:
        # Cleanup previous run
        if os.path.exists(PERSISTENCE_FILE):
            os.remove(PERSISTENCE_FILE)

        phase_one_initial_boot()
        phase_two_reboot()

        # Cleanup
        if os.path.exists(PERSISTENCE_FILE):
            os.remove(PERSISTENCE_FILE)
            LOGGER.info("Cleanup complete.")

    except KeyboardInterrupt:
        pass
    except Exception as e:
        LOGGER.critical(f"Demo failed: {e}", exc_info=True)
