from .base import UDMIBase, DEFAULT_UDMI_VERSION


class MetaData(UDMIBase):
    schema = "metadata.json"
    __slots__ = ["version", "timestamp", "system", "hash", "gateway", "pointset"]

    def __init__(self, timestamp, system, hash=None, gateway=None, pointset=None, version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)
        self.pointset = pointset
        self.gateway = gateway
        self.system = system
        self.hash = hash
        super().__init__(version)
