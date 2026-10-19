"""Sandboxed file access for UDMI Workbench (Layer 4).

Browsing and reading are confined to the UDMI repository root plus the
directories the operator has explicitly registered as site model search roots.
The v1 implementation exposed the entire `$HOME` directory while binding
`0.0.0.0`; here the reachable set is exactly what the operator consented to,
and every path outside it is refused by name.

Registered roots must be inside the sandbox, not merely discoverable: results
for an external site model live next to that model, so refusing to read them
would leave the artifact viewer permanently broken for anyone whose site
models are stored outside the checkout.
"""

import os
from typing import Any, Dict, Iterable, List

from workbench.server.paths import absolute, display, is_within, within_any

TEXT_SUFFIXES = {
    ".md", ".log", ".json", ".txt", ".out", ".attr", ".csv",
    ".yaml", ".yml", ".java", ".py", ".js", ".css", ".html", ".svg",
}
MAX_READ_BYTES = 4 * 1024 * 1024


class SandboxError(Exception):
    """Raised when a path escapes the consented sandbox or cannot be read."""


def resolve_within_root(
    udmi_root: str, relative_path: str, extra_roots: Iterable[str] = ()
) -> str:
    """Resolves a user-supplied path and proves it stays inside the sandbox."""
    if relative_path is None or relative_path == "":
        return os.path.realpath(udmi_root)

    candidate = os.path.realpath(absolute(udmi_root, relative_path))
    roots = list(extra_roots)

    if is_within(candidate, udmi_root) or within_any(candidate, roots):
        return candidate

    raise SandboxError(
        f"Path '{relative_path}' resolves outside the UDMI repository sandbox and outside "
        "every registered site model path. Register the directory that contains it first."
    )


def _boundaries(udmi_root: str, extra_roots: Iterable[str]) -> List[str]:
    return [udmi_root, *extra_roots]


def browse(
    udmi_root: str, relative_path: str = "", extra_roots: Iterable[str] = ()
) -> Dict[str, Any]:
    """Lists directories and files at a path inside the consented sandbox."""
    roots = list(extra_roots)
    target = resolve_within_root(udmi_root, relative_path, roots)
    if not os.path.isdir(target):
        raise SandboxError(f"Not a directory: {relative_path}")

    folders: List[Dict[str, Any]] = []
    files: List[Dict[str, Any]] = []
    for name in sorted(os.listdir(target)):
        if name.startswith("."):
            continue
        full = os.path.join(target, name)
        spelled = display(full, udmi_root)
        if os.path.isdir(full):
            folders.append({
                "name": name,
                "path": spelled,
                "is_site_model": os.path.isfile(os.path.join(full, "cloud_iot_config.json")),
            })
        elif os.path.isfile(full):
            files.append({"name": name, "path": spelled, "size": os.path.getsize(full)})

    # Traversal stops at every sandbox boundary; there is no path upward out of
    # a consented root, which is what keeps browsing from becoming a filesystem
    # explorer the way v1's was.
    at_boundary = any(
        os.path.realpath(target) == os.path.realpath(boundary)
        for boundary in _boundaries(udmi_root, roots)
    )
    parent = None
    if not at_boundary:
        parent = display(os.path.dirname(target), udmi_root)
        if parent == ".":
            parent = ""

    return {
        "path": "" if os.path.realpath(target) == os.path.realpath(udmi_root)
                else display(target, udmi_root),
        "parent": parent,
        "folders": folders,
        "files": files,
    }


def read_text(
    udmi_root: str, relative_path: str, extra_roots: Iterable[str] = ()
) -> Dict[str, Any]:
    """Reads a text artifact from within the consented sandbox."""
    target = resolve_within_root(udmi_root, relative_path, extra_roots)
    if not os.path.isfile(target):
        raise SandboxError(f"File not found: {relative_path}")

    suffix = os.path.splitext(target)[1].lower()
    if suffix and suffix not in TEXT_SUFFIXES:
        raise SandboxError(
            f"Refusing to read '{suffix}' file as text. Supported: {', '.join(sorted(TEXT_SUFFIXES))}"
        )

    size = os.path.getsize(target)
    if size > MAX_READ_BYTES:
        raise SandboxError(
            f"File is {size} bytes, exceeding the {MAX_READ_BYTES} byte read limit."
        )

    with open(target, "r", encoding="utf-8", errors="replace") as fh:
        content = fh.read()

    return {
        "path": display(target, udmi_root),
        "size": size,
        "content": content,
    }
