"""One-time Vertex AI provider setup for Mantis, saved per user.

`bin/mantis setup --vertex=<project>[/<region>]` saves the choice under the
`mantis` key of `~/.config/udmi/workbench.json` (`$XDG_CONFIG_HOME/udmi/...`),
the file that already holds the Workbench's site roots and notification
consent. `bin/mantis`, `bin/workbench` and the Workbench gateway apply it at
startup by exporting GOOGLE_CLOUD_PROJECT / GOOGLE_CLOUD_REGION, which is what
`mantis.config` and google-genai read.

A saved setup never silently loses to, or overrides, a provider named in the
environment: when both exist and disagree, startup is refused and both sources
are named.
"""

import json
import os
from typing import Any, Dict, Optional

CONFIG_RELATIVE_PATH = os.path.join("udmi", "workbench.json")
CONFIG_KEY = "mantis"
DEFAULT_REGION = "global"
PROVIDER_VERTEX = "vertex_ai"


class ProviderSetupError(Exception):
    """Raised when the saved setup cannot be read, written, or applied."""


def config_path() -> str:
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.join(os.path.expanduser("~"), ".config")
    return os.path.join(base, CONFIG_RELATIVE_PATH)


def _read_document(path: str) -> Dict[str, Any]:
    if not os.path.isfile(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as fh:
            document = json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        raise ProviderSetupError(
            f"Configuration at {path} cannot be read: {exc}. Repair or delete the file to continue."
        ) from exc
    if not isinstance(document, dict):
        raise ProviderSetupError(f"Configuration at {path} must be a JSON object.")
    return document


def _write_document(path: str, document: Dict[str, Any]) -> None:
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        temp_path = f"{path}.tmp"
        with open(temp_path, "w", encoding="utf-8") as fh:
            json.dump(document, fh, indent=2, sort_keys=True)
            fh.write("\n")
        os.replace(temp_path, path)
    except OSError as exc:
        raise ProviderSetupError(f"Cannot save configuration to {path}: {exc}") from exc


def load(path: Optional[str] = None) -> Optional[Dict[str, str]]:
    """Returns the saved setup `{"provider", "project", "region"}`, or None."""
    path = path or config_path()
    record = _read_document(path).get(CONFIG_KEY)
    if record is None:
        return None
    if (
        not isinstance(record, dict)
        or record.get("provider") != PROVIDER_VERTEX
        or not isinstance(record.get("project"), str) or not record["project"]
        or not isinstance(record.get("region"), str) or not record["region"]
    ):
        raise ProviderSetupError(
            f"The '{CONFIG_KEY}' entry in {path} is invalid: {record!r}. "
            "Run 'bin/mantis setup --clear', then set it up again."
        )
    return record


def parse_vertex_spec(spec: str) -> Dict[str, str]:
    """Parses `<project>[/<region>]`."""
    project, _, region = spec.partition("/")
    if not project or "/" in region or (spec.endswith("/") and not region):
        raise ProviderSetupError(f"Expected --vertex=<project>[/<region>], got '--vertex={spec}'.")
    return {"provider": PROVIDER_VERTEX, "project": project, "region": region or DEFAULT_REGION}


def save(record: Dict[str, str], path: Optional[str] = None) -> str:
    path = path or config_path()
    document = _read_document(path)
    document[CONFIG_KEY] = record
    _write_document(path, document)
    return path


def clear(path: Optional[str] = None) -> bool:
    """Removes the saved setup. Returns whether there was one."""
    path = path or config_path()
    document = _read_document(path)
    if CONFIG_KEY not in document:
        return False
    del document[CONFIG_KEY]
    _write_document(path, document)
    return True


def conflict(record: Dict[str, str]) -> Optional[str]:
    """Names every environment variable that disagrees with the saved setup."""
    clashes = []
    for name in ("GEMINI_API_KEY", "GOOGLE_API_KEY"):
        if os.getenv(name):
            clashes.append(f"{name} is set, which selects Google AI Studio")
    if os.getenv("MANTIS_OFFLINE", "").lower() in ("true", "1", "yes"):
        clashes.append("MANTIS_OFFLINE is set, which selects offline mode")
    for name in ("GOOGLE_CLOUD_PROJECT", "GCP_PROJECT"):
        value = os.getenv(name)
        if value and value != record["project"]:
            clashes.append(f"{name} is set to '{value}'")
    for name in ("GOOGLE_CLOUD_REGION", "GCP_REGION"):
        value = os.getenv(name)
        if value and value != record["region"]:
            clashes.append(f"{name} is set to '{value}'")
    if not clashes:
        return None
    return (
        f"The saved Mantis setup (Vertex AI project '{record['project']}', region "
        f"'{record['region']}', in {config_path()}) conflicts with the environment: "
        f"{'; '.join(clashes)}. Unset those variables, or run 'bin/mantis setup --clear'."
    )


def apply_to_environment() -> Optional[str]:
    """Exports the saved setup for this process. Returns a problem, or None."""
    try:
        record = load()
    except ProviderSetupError as exc:
        return str(exc)
    if record is None:
        return None
    problem = conflict(record)
    if problem:
        return problem
    os.environ["GOOGLE_CLOUD_PROJECT"] = record["project"]
    os.environ["GOOGLE_CLOUD_REGION"] = record["region"]
    return None
