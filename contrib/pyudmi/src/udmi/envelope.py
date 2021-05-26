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
