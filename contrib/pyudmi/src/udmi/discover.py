
"""
{
  "title": "Device discover schema",
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
    "protocol": {
      "type": "string"
    },
    "local_id": {
      "type": "string"
    },
    "points": {
      "additionalProperties": false,
      "patternProperties": {
        "^[a-z][a-z0-9]*(_[a-z0-9]+)*$": {
          "$ref": "#/definitions/point_property_names"
        }
      }
    }
  },
  "required": [
    "timestamp",
    "version",
    "protocol",
    "local_id",
    "points"
  ],
  "definitions": {
    "point_property_names": {
      "propertyNames": {
        "oneOf": [
          {
            "enum": [
              "units",
              "present_value"
            ]
          }
        ]
      }
    }
  }
}

"""
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
