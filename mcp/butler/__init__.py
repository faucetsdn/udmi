"""UDMI Butler MCP Server Package."""

__all__ = ["ButlerClient", "ButlerProvider", "ButlerMcpServer"]


def __getattr__(name):
    if name == "ButlerClient":
        from mcp.butler.client import ButlerClient
        return ButlerClient
    if name == "ButlerProvider":
        from mcp.butler.provider import ButlerProvider
        return ButlerProvider
    if name == "ButlerMcpServer":
        from mcp.butler.server import ButlerMcpServer
        return ButlerMcpServer
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")

