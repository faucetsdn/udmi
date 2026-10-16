"""Server-side Mermaid rendering to PNG for notification emails (Layer 4).

Email clients do not run JavaScript, and a notification must be composable after
the operator has closed the Workbench tab, so diagrams are rendered here rather
than in the browser. Headless Chromium (Playwright) loads the same vendored
`mermaid.min.js` the Workbench UI uses, with the same configuration, so the image
in the inbox matches the diagram on screen.

A diagram that fails to render raises MermaidRenderError with Mermaid's own
message; the composer shows that message in place of the image instead of
silently dropping the diagram.
"""

import os
from typing import List

MERMAID_JS = os.path.join("workbench", "static", "js", "vendor", "mermaid.min.js")
DEVICE_SCALE = 2  # Crisp on high-density displays; email clients scale the image down.
RENDER_TIMEOUT_MS = 30000

_PAGE = """<!doctype html><html><head><meta charset="utf-8">
<style>body{margin:0;background:#fff}#out{display:inline-block;padding:16px}</style>
</head><body><div id="out"></div></body></html>"""

_RENDER_JS = """async (code) => {
  window.mermaid.initialize({startOnLoad: false, theme: 'default',
                             securityLevel: 'strict', htmlLabels: false,
                             flowchart: {htmlLabels: false}});
  try {
    const res = await window.mermaid.render('mmd' + Date.now(), code);
    document.getElementById('out').innerHTML = res.svg;
    return null;
  } catch (e) {
    return String(e && e.message ? e.message : e);
  }
}"""


class MermaidRenderError(Exception):
    """Raised when Chromium or Mermaid cannot produce an image for a diagram."""


def render_pngs(udmi_root: str, diagrams: List[str]) -> List[bytes]:
    """Renders each Mermaid source to PNG bytes, in order. Raises on the first failure."""
    if not diagrams:
        return []
    script_path = os.path.join(udmi_root, MERMAID_JS)
    if not os.path.isfile(script_path):
        raise MermaidRenderError(f"Mermaid library not found at {script_path}.")
    try:
        from playwright.sync_api import Error as PlaywrightError
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        raise MermaidRenderError(
            f"Playwright is not installed ({exc}); run bin/setup_base to install etc/requirements.txt."
        ) from exc

    images: List[bytes] = []
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch()
            try:
                page = browser.new_page(device_scale_factor=DEVICE_SCALE)
                page.set_default_timeout(RENDER_TIMEOUT_MS)
                page.set_content(_PAGE)
                page.add_script_tag(path=script_path)
                for index, code in enumerate(diagrams, start=1):
                    error = page.evaluate(_RENDER_JS, code)
                    if error:
                        raise MermaidRenderError(f"Diagram {index} is not valid Mermaid: {error}")
                    images.append(page.locator("#out").screenshot(type="png"))
            finally:
                browser.close()
    except PlaywrightError as exc:
        raise MermaidRenderError(
            f"Headless Chromium failed ({exc}). If the browser is missing, run: "
            "venv/bin/playwright install chromium"
        ) from exc
    return images

