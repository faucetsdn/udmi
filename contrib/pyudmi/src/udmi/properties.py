"""
{
  "title": "Device Properties Schema",
  "type": "object",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "additionalProperties": false,
  "required": [
    "key_type",
    "version",
    "connect"
  ],
  "properties": {
    "key_type": {
      "enum": [
        "RSA_PEM",
        "RSA_X509_PEM"
      ]
    },
    "version": {
      "enum": [
        1
      ]
    },
    "connect": {
      "enum": [
        "direct"
      ]
    }
  }
}

"""

from .base import UDMIBase, DEFAULT_UDMI_VERSION


class Properties(UDMIBase):

    schema = "properties.json"
    __slots__ = ["version", "key_type", "connect"]

    def __init__(self, key_type, connect, version=DEFAULT_UDMI_VERSION):

        self.key_type = key_type
        self.connect = connect
        super().__init__(version)
