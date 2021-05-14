"""
{
  "title": "Message envelope schema",
  "additionalProperties": true,
  "properties": {
    "deviceId": {
      "type": "string",
      "pattern": "^[A-Z]{2,6}-[0-9]{1,6}$"
    },
    "deviceNumId": {
      "type": "string",
      "pattern": "^[0-9]+$"
    },
    "deviceRegistryId": {
      "type": "string",
      "pattern": "^[a-zA-Z][-a-zA-Z0-9._+~%]*[a-zA-Z0-9]$"
    },
    "projectId": {
      "type": "string",
      "pattern": "^([.a-z]+:)?[a-z][-a-z0-9]*[a-z0-9]$"
    },
    "subFolder": {
      "enum": [
        "config",
        "discover",
        "system",
        "metadata",
        "pointset",
        "state"
      ]
    }
  },
  "required": [
    "projectId",
    "deviceRegistryId",
    "deviceNumId",
    "deviceId",
    "subFolder"
  ]
}

"""

from .base import UDMIBase, DEFAULT_UDMI_VERSION


class Envelope(UDMIBase):

    schema = "envelope.json"
    __slots__ = [
        "projectId",
        "deviceRegistryId",
        "deviceNumId",
        "deviceId",
        "subFolder"
    ]

    def __init__(self, projectId, deviceRegistryId, deviceNumId, deviceId, subFolder, version=None):

        self.projectId = projectId
        self.deviceRegistryId = deviceRegistryId
        self.deviceNumId = deviceNumId
        self.deviceId = deviceId
        self.subFolder = subFolder
        super().__init__(version)