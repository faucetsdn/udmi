"""Read-only access to the UDMI specification for links in Mantis answers (Layer 4).

Mantis cites the governing specification by repository-relative path
(`docs/specs/...md`, `schema/state.json`). Those citations are rendered as links
to this endpoint so an operator or device manufacturer can open the exact text the
agent read, from the checkout under test rather than from a remote branch that may
have moved on.

The reachable set is deliberately narrow: markdown under `docs/` and JSON under
`schema/`, nothing else. Files are returned as `text/plain` with `nosniff`, so a
document can never be interpreted by the browser as HTML or script.
"""

import os
from typing import Tuple

from workbench.server.artifacts import SandboxError
from workbench.server.paths import is_within

# Top-level directory -> the one file suffix it serves.
ALLOWED_TREES = {"docs": ".md", "schema": ".json"}
MAX_DOC_BYTES = 2 * 1024 * 1024


class RepoDocError(SandboxError):
    """Raised for a doc path that is refused or missing.

    Subclasses SandboxError so the gateway's existing classification applies:
    a message containing "not found" maps to 404, any other refusal to 403.
    """


def resolve_doc(udmi_root: str, relative_path: str) -> Tuple[str, str]:
    """Validates a doc path and returns (absolute path, canonical relative path)."""
    if not relative_path:
        raise RepoDocError("Refusing doc request: 'path' is empty.")
    if "\x00" in relative_path or "\\" in relative_path:
        raise RepoDocError(f"Refusing doc path '{relative_path}': it contains an illegal character.")
    if relative_path.startswith("/") or os.path.isabs(relative_path):
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': it must be relative to the UDMI root."
        )
    parts = relative_path.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': empty, '.' and '..' segments are not allowed."
        )
    tree = parts[0]
    suffix = ALLOWED_TREES.get(tree)
    if suffix is None or len(parts) < 2:
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': only files under "
            f"{', '.join(f'{name}/' for name in sorted(ALLOWED_TREES))} are served."
        )
    if not relative_path.lower().endswith(suffix):
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': files under {tree}/ are served only "
            f"with the '{suffix}' suffix."
        )

    tree_root = os.path.join(udmi_root, tree)
    target = os.path.realpath(os.path.join(udmi_root, *parts))
    # realpath on both sides: a symlink inside docs/ cannot point the read elsewhere.
    if not is_within(target, tree_root):
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': it resolves outside {tree}/."
        )
    if not os.path.isfile(target):
        raise RepoDocError(f"Doc not found: {relative_path}")
    return target, relative_path


def read_doc(udmi_root: str, relative_path: str) -> bytes:
    """Returns the raw bytes of an allowed doc, enforcing the size limit."""
    target, _ = resolve_doc(udmi_root, relative_path)
    size = os.path.getsize(target)
    if size > MAX_DOC_BYTES:
        raise RepoDocError(
            f"Refusing doc path '{relative_path}': {size} bytes exceeds the "
            f"{MAX_DOC_BYTES} byte limit."
        )
    with open(target, "rb") as handle:
        return handle.read()


def serve_doc(responder, udmi_root: str, relative_path: str) -> None:
    """Writes an allowed doc to the client as inline, non-sniffable plain text."""
    content = read_doc(udmi_root, relative_path)
    handler = responder.handler
    handler.send_response(200)
    handler.send_header("Content-Type", "text/plain; charset=utf-8")
    handler.send_header("Content-Length", str(len(content)))
    handler.send_header("X-Content-Type-Options", "nosniff")
    handler.send_header("Content-Security-Policy", "default-src 'none'; sandbox")
    handler.send_header("Content-Disposition", "inline")
    handler.send_header("X-Correlation-ID", responder.correlation_id)
    handler.end_headers()
    handler.wfile.write(content)
