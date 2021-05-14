"""
{
  "title": "System event schema",
  "type": "object",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "additionalProperties": false,
  "properties": {
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "version": {
      "enum": [
        1
      ]
    },
    "logentries": {
      "type": "array",
      "items": {
        "$ref": "file:common.json#/definitions/entry"
      }
    }
  },
  "required": [
    "timestamp",
    "version"
  ]
}
"""
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
