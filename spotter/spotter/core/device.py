import logging
from typing import Optional

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration, SystemState, StateSystemHardware

from spotter.ota.handler import SpotterOTAHandler
from spotter.core.constants import SPOTTER_MAKE, SPOTTER_MODEL, SPOTTER_FIRMWARE_VER, SPOTTER_DEPENDENCIES

LOGGER = logging.getLogger(__name__)

class SpotterDevice:
    """
    Spotter Device Implementation.
    Spotter is a UDMI reference client running on-prem that can handle
    OTA updates and extensible capabilities over the Sequencer CI framework.
    """
    def __init__(self, endpoint: EndpointConfiguration, persist_path: str = "/tmp/spotter_persist.json", key_file: Optional[str] = None):
        self.endpoint = endpoint
        self.persist_path = persist_path

        # Define static device identity
        self.static_info = SystemState(
            hardware=StateSystemHardware(make=SPOTTER_MAKE, model=SPOTTER_MODEL),
            serial_no="SPOTTER-001",
            software={"firmware": SPOTTER_FIRMWARE_VER}
        )

        self.device = create_device(
            endpoint,
            system_state=self.static_info,
            persistence_path=self.persist_path,
            key_file=key_file
        )
        self.sys_manager = self.device.get_manager(SystemManager)

        # Initialize the OTA Handler
        self.ota_handler = SpotterOTAHandler(
            hardware_make=SPOTTER_MAKE,
            hardware_model=SPOTTER_MODEL,
            current_dependencies=SPOTTER_DEPENDENCIES
        )

        # Register the OTA blob handler for 'firmware'
        self.sys_manager.register_blob_handler(
            "firmware",
            process=self.ota_handler.process,
            post_process=self.ota_handler.post_process,
            expects_file=False
        )

    def run(self):
        LOGGER.info("Spotter running. Waiting for OTA...")
        self.device.run()
