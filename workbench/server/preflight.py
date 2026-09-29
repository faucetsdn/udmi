"""Launch prerequisites for the Workbench gateway.

`bin/workbench` runs `python -m workbench.server.preflight` before starting the
gateway. It adds the Workbench's own needs to the Mantis CLI checks (the
gateway hosts the Mantis agent), and exits non-zero with every problem listed.
"""

import os
import subprocess
import sys
from typing import Callable, List, Optional

from mantis import preflight

#: Modules the gateway imports on top of Mantis's: Markdown for email bodies,
#: the Gmail client and Google auth for notifications, Playwright for rendering
#: Mermaid diagrams into email images.
WORKBENCH_MODULES = ("markdown2", "googleapiclient.discovery", "google.auth", "playwright.sync_api")


def check_chromium() -> Optional[str]:
    """Playwright's Chromium renders Mermaid diagrams into notification emails.

    Asks `playwright install --dry-run chromium` where the browsers belong (the
    full browser and the headless shell that `chromium.launch()` uses) and checks
    those directories exist. Nothing is launched or downloaded.
    """
    try:
        result = subprocess.run(
            [sys.executable, "-m", "playwright", "install", "--dry-run", "chromium"],
            capture_output=True, text=True, timeout=60,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"Cannot ask Playwright where Chromium is installed ({exc}). {preflight.SETUP_HINT}"
    if result.returncode != 0:
        return f"Playwright cannot report its browsers ({result.stderr.strip()}). {preflight.SETUP_HINT}"
    missing = []
    header = None
    for line in result.stdout.splitlines():
        if line and not line.startswith(" "):
            header = line
        elif line.strip().startswith("Install location:") and header and "(playwright chromium" in header:
            location = line.split(":", 1)[1].strip()
            if not os.path.isdir(location):
                missing.append(location)
    if missing:
        return (
            f"Playwright's Chromium is not installed (missing {', '.join(missing)}); the Workbench "
            "uses it to render diagrams in notification emails. Run: venv/bin/playwright install chromium"
        )
    return None


def workbench_checks() -> List[Callable[[], Optional[str]]]:
    return preflight.mantis_checks("cli") + [
        lambda: preflight.check_modules(WORKBENCH_MODULES),
        check_chromium,
        lambda: preflight.check_command("java", "bin/sequencer runs the Java sequencer"),
    ]


def main() -> int:
    problems = preflight.run(workbench_checks())
    return 0 if preflight.report(problems, "The UDMI Workbench") else 1


if __name__ == "__main__":
    sys.exit(main())
