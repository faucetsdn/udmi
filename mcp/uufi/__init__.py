"""UDMI Unified UDMI Functional Interface (UUFI) MCP Server Package.

Encapsulates external-facing messaging transport (MQTT/UUFI), Layer 1 service handshakes,
device configuration mutations, system model operations, live state queries, and
event stream ingress according to the authoritative UUFI specification (docs/specs/uufi.md).
"""

__all__ = ["UUFIProvider", "UUFIClient", "UUFIMcpServer"]


def __getattr__(name):
    if name == "UUFIClient":
        from mcp.uufi.client import UUFIClient
        return UUFIClient
    if name == "UUFIProvider":
        from mcp.uufi.provider import UUFIProvider
        return UUFIProvider
    if name == "UUFIMcpServer":
        from mcp.uufi.server import UUFIMcpServer
        return UUFIMcpServer
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")

