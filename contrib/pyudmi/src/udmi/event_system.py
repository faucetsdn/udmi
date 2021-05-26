import copy
from .base import UDMIBase, DEFAULT_UDMI_VERSION


class EventSystem(UDMIBase):
    schema = "event_system.json"
    __slots__ = ["version", "timestamp", "logentries"]

    def __init__(self, timestamp, logentries, version=DEFAULT_UDMI_VERSION):
        self.timestamp = self.serialise_timestamp(timestamp)

        def munge_timestamp(l):
            with_timestamp = copy.deepcopy(l)
            with_timestamp["timestamp"] = self.serialise_timestamp(with_timestamp["timestamp"])
            return with_timestamp

        self.logentries = [munge_timestamp(l) for l in logentries]
        super().__init__(version)
