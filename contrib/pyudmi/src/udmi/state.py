from .base import UDMIBase, DEFAULT_UDMI_VERSION


class State(UDMIBase):
    schema = "state.json"
    __slots__ = ["version", "system", "timestamp", "pointset"]

    def __init__(self, timestamp, system, pointset=None, version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)
        self.system = system
        self.pointset = pointset
        super().__init__(version)
