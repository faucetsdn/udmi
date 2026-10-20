"""Session manager import compatibility shim for Mantis.

Allows Mantis to seamlessly use SessionManager whether running against master
(mcp.session_manager) or grafnu/mcps (mcp.infra.session_manager).
"""

try:
    from mcp.infra.session_manager import SessionManager
except ImportError:
    from mcp.session_manager import SessionManager

__all__ = ["SessionManager"]
