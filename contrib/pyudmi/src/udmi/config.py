from datetime import datetime
from .base import UDMIBase, DEFAULT_UDMI_VERSION


class Config(UDMIBase):
    schema = "config.json"
    __slots__ = ["version", "timestamp", "system", "pointset", "gateway"]

    def __init__(self, timestamp: (str, datetime),
                 system: dict,
                 pointset: dict = None,
                 gateway: dict = None,
                 version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)
        self.system = system
        self.pointset = pointset
        self.gateway = gateway
        super().__init__(version)
