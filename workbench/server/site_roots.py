"""User-consented site model search roots (Layer 4).

Site models are routinely kept outside the UDMI checkout. Under WSL the
repository and the lab's site models are commonly on different filesystems
altogether, which made the models unreachable from the Workbench entirely.
Rather than re-exposing a broad slice of the filesystem the way
the v1 server did with `$HOME`, the Workbench reads site models from exactly
two places:

  * `<udmi_root>/sites/` -- always scanned, it is part of the checkout.
  * Directories the operator has explicitly registered through this registry.

Registration IS the consent record: nothing outside the checkout is scanned,
listed, or read until its containing directory has been registered, and every
registration is persisted so it survives a Workbench restart. Removing a root
withdraws that consent immediately.

The registry is deliberately server-side. The server is the process that
touches the filesystem, so the record of what it is permitted to touch has to
live where the access decision is made -- not in a browser that any other
client could contradict.
"""

import json
import os
import threading
from typing import Any, Dict, List

from workbench.server import discovery
from workbench.server.logger import SERVER_LOGGER
from workbench.server.paths import absolute, is_within

CONFIG_RELATIVE_PATH = os.path.join("udmi", "workbench.json")
CONFIG_KEY = "site_roots"


class SiteRootError(Exception):
    """Raised when a search root cannot be registered, read, or removed."""


def default_config_path() -> str:
    """Returns the XDG location of the persisted Workbench configuration."""
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.join(os.path.expanduser("~"), ".config")
    return os.path.join(base, CONFIG_RELATIVE_PATH)


class SiteRootRegistry:
    """Persisted allowlist of directories the Workbench may scan for models."""

    def __init__(self, udmi_root: str, config_path: str = None):
        self.udmi_root = os.path.abspath(udmi_root)
        self.config_path = config_path or default_config_path()
        self._lock = threading.Lock()

    # ------------------------------------------------------------ storage ---
    def _read_document(self) -> Dict[str, Any]:
        if not os.path.isfile(self.config_path):
            return {}
        try:
            with open(self.config_path, "r", encoding="utf-8") as fh:
                document = json.load(fh)
        except (OSError, json.JSONDecodeError) as exc:
            raise SiteRootError(
                f"Workbench configuration at {self.config_path} cannot be read: {exc}. "
                "Repair or delete the file to continue."
            ) from exc
        if not isinstance(document, dict):
            raise SiteRootError(
                f"Workbench configuration at {self.config_path} must be a JSON object."
            )
        return document

    def _write_document(self, document: Dict[str, Any]) -> None:
        directory = os.path.dirname(self.config_path)
        try:
            os.makedirs(directory, exist_ok=True)
            with open(self.config_path, "w", encoding="utf-8") as fh:
                json.dump(document, fh, indent=2, sort_keys=True)
                fh.write("\n")
        except OSError as exc:
            raise SiteRootError(
                f"Cannot save Workbench configuration to {self.config_path}: {exc}"
            ) from exc

    def paths(self) -> List[str]:
        """Returns every registered root as an absolute path."""
        document = self._read_document()
        roots = document.get(CONFIG_KEY, [])
        if not isinstance(roots, list) or not all(isinstance(root, str) for root in roots):
            raise SiteRootError(
                f"'{CONFIG_KEY}' in {self.config_path} must be a list of directory paths."
            )
        return [absolute(self.udmi_root, root) for root in roots]

    # ----------------------------------------------------------- querying ---
    def describe(self) -> Dict[str, Any]:
        """Reports every registered root and what is currently visible in it."""
        entries: List[Dict[str, Any]] = []
        for root in self.paths():
            entry: Dict[str, Any] = {"path": root, "available": os.path.isdir(root)}
            if entry["available"]:
                try:
                    entry["site_model_count"] = len(
                        discovery.scan_for_site_models(root, self.udmi_root)
                    )
                except discovery.DiscoveryError as exc:
                    entry["available"] = False
                    entry["reason"] = str(exc)
            else:
                entry["reason"] = "Directory is missing or no longer readable."
            entries.append(entry)
        return {"site_roots": entries, "config_path": self.config_path}

    # -------------------------------------------------------- mutation ------
    def register(self, path: str, correlation_id: str = None) -> Dict[str, Any]:
        """Records consent to scan `path`, after proving it holds site models.

        A directory containing no site model is refused outright. That is the
        single most common mistake -- pointing at a parent two levels too high
        or at the wrong drive -- and refusing it here turns a silently empty
        site model list into an immediate, specific error.
        """
        if not path or not path.strip():
            raise SiteRootError("A directory path is required.")

        candidate = absolute(self.udmi_root, path.strip())

        if not os.path.exists(candidate):
            raise SiteRootError(f"No such directory: {candidate}")
        if not os.path.isdir(candidate):
            raise SiteRootError(f"Not a directory: {candidate}")
        if not os.access(candidate, os.R_OK | os.X_OK):
            raise SiteRootError(f"Directory is not readable by this process: {candidate}")

        sites_root = os.path.join(self.udmi_root, "sites")
        if is_within(candidate, sites_root):
            raise SiteRootError(
                f"'{candidate}' is inside the repository's sites/ directory, which is "
                "always scanned. No registration is needed."
            )

        found = discovery.scan_for_site_models(candidate, self.udmi_root)
        if not found:
            raise SiteRootError(
                f"No site model found in '{candidate}' or its immediate subdirectories. "
                f"A site model is a directory containing {discovery.SITE_CONFIG_FILENAME}; "
                "register either the model itself or the folder that holds them."
            )

        with self._lock:
            document = self._read_document()
            existing = document.get(CONFIG_KEY, [])
            if any(
                os.path.realpath(absolute(self.udmi_root, root)) == os.path.realpath(candidate)
                for root in existing
            ):
                raise SiteRootError(f"'{candidate}' is already registered.")
            document[CONFIG_KEY] = [*existing, candidate]
            self._write_document(document)

        SERVER_LOGGER.info(
            "SiteRootRegistry",
            "site_root.registered",
            correlation_id=correlation_id,
            context={"path": candidate},
            details={"siteModelCount": len(found), "configPath": self.config_path},
        )
        return {"path": candidate, "site_model_count": len(found), "site_models": found}

    def unregister(self, path: str, correlation_id: str = None) -> Dict[str, Any]:
        """Withdraws consent for `path`. Fails if it was never registered."""
        if not path or not path.strip():
            raise SiteRootError("A directory path is required.")

        candidate = os.path.realpath(absolute(self.udmi_root, path.strip()))

        with self._lock:
            document = self._read_document()
            existing = document.get(CONFIG_KEY, [])
            remaining = [
                root for root in existing
                if os.path.realpath(absolute(self.udmi_root, root)) != candidate
            ]
            if len(remaining) == len(existing):
                raise SiteRootError(f"'{candidate}' is not a registered site model path.")
            document[CONFIG_KEY] = remaining
            self._write_document(document)

        SERVER_LOGGER.info(
            "SiteRootRegistry",
            "site_root.unregistered",
            correlation_id=correlation_id,
            context={"path": candidate},
            details={"configPath": self.config_path},
        )
        return {"path": candidate, "removed": True}
