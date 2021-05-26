from datetime import datetime
from .base import UDMIBase, DEFAULT_UDMI_VERSION


class Discover(UDMIBase):
    schema = "discover.json"
    __slots__ = ["version", "timestamp", "protocol", "local_id", "points"]

    def __init__(self, timestamp: datetime, protocol: str, local_id: str, points: dict, version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)
        self.protocol = protocol
        self.local_id = local_id
        self.points = points
        super().__init__(version)
