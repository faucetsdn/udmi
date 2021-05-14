"""
{
  "title": "Device Config Schema",
  "type": "object",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "additionalProperties": false,
  "required": [
    "timestamp",
    "version"
  ],
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
    "system": {
      "$ref": "file:config_system.json#"
    },
    "gateway": {
      "$ref": "file:config_gateway.json#"
    },
    "localnet": {
      "$ref": "file:config_localnet.json#"
    },
    "pointset": {
      "$ref": "file:config_pointset.json#"
    }
  }
}

"""

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
