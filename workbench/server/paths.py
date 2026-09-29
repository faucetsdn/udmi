"""Filesystem path helpers shared across the Workbench server (Layer 4).

Site models are not always inside the UDMI checkout, so the server constantly
has to answer two questions about a path: "is it inside a directory we are
allowed to touch?" and "how should it be spelled back to the client?". Both
answers live here so that discovery, the artifact sandbox, and the site-root
registry cannot drift apart in how they resolve or present a path.
"""

import os
from typing import Iterable


def absolute(base: str, path: str) -> str:
    """Expands `~`, anchors a relative path to `base`, and returns an abspath.

    `base` is the UDMI repository root everywhere it is used, which keeps
    repository-relative handles such as `sites/udmi_site_model` working while
    still accepting a fully qualified path to a model stored elsewhere.
    """
    candidate = os.path.expanduser(path)
    if not os.path.isabs(candidate):
        candidate = os.path.join(base, candidate)
    return os.path.abspath(candidate)


def is_within(target: str, root: str) -> bool:
    """True when `target` is `root` itself or lives underneath it.

    Both sides are resolved through `realpath` first, so a symlink cannot be
    used to smuggle a path past a containment check.
    """
    target_real = os.path.realpath(target)
    root_real = os.path.realpath(root)
    return target_real == root_real or target_real.startswith(root_real + os.sep)


def within_any(target: str, roots: Iterable[str]) -> bool:
    """True when `target` is contained by at least one of `roots`."""
    return any(is_within(target, root) for root in roots)


def display(target: str, udmi_root: str) -> str:
    """Spells a path for the client: repo-relative inside, absolute outside.

    A relative spelling keeps in-repo paths short and portable, but applying
    it to an external site model would produce a `../../..` string that no
    containment check can validate. External paths are therefore returned
    fully qualified and are used verbatim on the way back in.
    """
    if is_within(target, udmi_root):
        return os.path.relpath(os.path.realpath(target), os.path.realpath(udmi_root))
    return os.path.realpath(target)
