from .base import UDMIBase, DEFAULT_UDMI_VERSION


class EventPointset(UDMIBase):
    schema = "event_pointset.json"
    __slots__ = ["version", "timestamp", "points"]

    def __init__(self, timestamp, points, version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)
        self.points = points
        super().__init__(version)


Pointset = EventPointset
