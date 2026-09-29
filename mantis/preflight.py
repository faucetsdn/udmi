"""Launch prerequisites for Mantis, checked before any work starts.

`bin/mantis` (through `mantis.cli`) and `bin/workbench` (through
`workbench.server.preflight`) call this so a missing prerequisite stops the
launch with the exact fix, instead of surfacing minutes later as an opaque
failure inside a tool call or model request.

Each check returns a problem string, or None when the prerequisite is met.
"""

import importlib
import os
import shutil
import sys
from typing import Callable, List, Optional, Sequence

from mantis import provider_setup

SETUP_HINT = "Run bin/setup_base to rebuild the virtualenv."
SYSTEM_SETUP_HINT = "Install it with 'sudo bin/setup_base' (system packages need root)."

#: Python modules every Mantis entry point imports. Kept to modules that are
#: pinned in etc/requirements.txt.
MANTIS_MODULES = ("google.genai", "pydantic", "yaml")

#: The UDMI common Python package, found only when common/src/main/python is on
#: PYTHONPATH. Without it Mantis silently loses project-spec parsing and
#: publishing to non-local brokers (both imports are guarded by ImportError).
UDMI_COMMON_MODULES = ("udmi.common.project_spec", "udmi.common.connection")

#: Mantis modes that call a model. The MCP server serves deterministic tools only.
MODES = ("cli", "mcp")


def check_modules(modules: Sequence[str], hint: str = SETUP_HINT) -> Optional[str]:
    missing = []
    for name in modules:
        try:
            importlib.import_module(name)
        except ImportError as exc:
            missing.append(f"{name} ({exc})")
    if missing:
        return f"Python modules cannot be imported: {', '.join(missing)}. {hint}"
    return None


def check_udmi_common() -> Optional[str]:
    return check_modules(
        UDMI_COMMON_MODULES,
        hint="Put common/src/main/python on PYTHONPATH (bin/mantis and bin/workbench do this).",
    )


def check_command(command: str, purpose: str) -> Optional[str]:
    if shutil.which(command) is None:
        return f"'{command}' is not on PATH; {purpose}. {SYSTEM_SETUP_HINT}"
    return None


def check_provider() -> Optional[str]:
    """Mirrors mantis.config.MantisConfig.provider: offline, AI Studio, else Vertex AI.

    For Vertex AI, google-genai takes the project from GOOGLE_CLOUD_PROJECT, else
    from Application Default Credentials (`google.auth.default()`), so a project
    from either source is a configured provider.
    """
    if os.getenv("MANTIS_OFFLINE", "").lower() in ("true", "1", "yes"):
        return None
    if os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY"):
        return None
    if os.getenv("GOOGLE_CLOUD_PROJECT") or os.getenv("GCP_PROJECT"):
        return None
    problem = adc_problem(require_project=True)
    if problem is None:
        return None
    adc_problem_text = (
        f"no GEMINI_API_KEY, GOOGLE_API_KEY, GOOGLE_CLOUD_PROJECT or saved setup, and {problem}"
    )
    return (
        f"Mantis has no model provider configured ({adc_problem_text}). Choose one:\n"
        "  1. Google AI Studio API key:\n"
        "       export GEMINI_API_KEY=<key>      # create one at https://aistudio.google.com/apikey\n"
        "  2. Your own Vertex AI project, with application-default credentials (one-time setup):\n"
        "       gcloud auth application-default login\n"
        "       bin/mantis setup --vertex=<your-project-id>[/<region>]\n"
        "  3. Offline deterministic mode (no model):\n"
        "       export MANTIS_OFFLINE=true\n"
        "See mantis/README.md (Model provider credentials) for details."
    )


def adc_problem(require_project: bool = False) -> Optional[str]:
    """Why application-default credentials are unusable, or None."""
    try:
        import google.auth
        from google.auth.exceptions import DefaultCredentialsError
    except ImportError as exc:
        return f"google-auth cannot be imported: {exc}"
    try:
        _, project = google.auth.default()
    except DefaultCredentialsError as exc:
        return f"no application-default credentials: {exc}"
    if require_project and not project:
        return "the application-default credentials name no project"
    return None


def mantis_checks(mode: str) -> List[Callable[[], Optional[str]]]:
    if mode not in MODES:
        raise ValueError(f"Unknown Mantis preflight mode '{mode}'; expected one of {MODES}.")
    checks: List[Callable[[], Optional[str]]] = [
        lambda: check_modules(MANTIS_MODULES),
        check_udmi_common,
        lambda: check_command("tmux", "Mantis runs local test setups and reads logs in tmux sessions"),
    ]
    if mode == "cli":
        # The saved setup is exported first, so check_provider sees it.
        checks += [provider_setup.apply_to_environment, check_provider]
    return checks


def run(checks: Sequence[Callable[[], Optional[str]]]) -> List[str]:
    return [problem for problem in (check() for check in checks) if problem]


def report(problems: Sequence[str], app: str) -> bool:
    """Prints every problem to stderr. Returns True when there were none."""
    if not problems:
        return True
    print(f"Error: {app} cannot start until these prerequisites are fixed:", file=sys.stderr)
    for problem in problems:
        print(f"- {problem}", file=sys.stderr)
    return False
