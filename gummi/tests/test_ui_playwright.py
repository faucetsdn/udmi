"""End-to-End browser UI automation test suite using Playwright."""

import os
import sys
import threading
import time
from typing import List
import pytest
from playwright.sync_api import sync_playwright, Page, Browser, expect

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gummi"))
sys.path.insert(0, REPO_ROOT)

from gummi.src.server import GummiServer, ThreadingHTTPServer, GummiRequestHandler


@pytest.fixture(scope="module")
def gummi_server_url():
    """Spawns an in-process GUMMI server on a dynamic port."""
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()

    server = GummiServer(host="127.0.0.1", port=port)
    server_address = (server.host, server.port)
    httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
    httpd.daemon_threads = True
    httpd.db = server.db
    httpd.uufi = server.uufi
    server.httpd = httpd

    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    time.sleep(0.2)

    base_url = f"http://127.0.0.1:{port}"
    yield base_url

    try:
        httpd.server_close()
    except Exception:
        pass
    server.uufi.stop()


@pytest.fixture(scope="module")
def browser_context():
    """Launches a headless Chromium browser instance."""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        yield browser
        browser.close()


def test_gummi_page_load_and_navigation(gummi_server_url: str, browser_context: Browser):
    """Verifies page load, zero JavaScript runtime errors, and tab navigation."""
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    # 1. Load root page
    page.goto(gummi_server_url)
    page.wait_for_load_state("domcontentloaded")

    # Assert no JS errors occurred during load
    assert len(page_errors) == 0, f"JavaScript errors detected on load: {page_errors}"

    # Assert brand header and initial tab
    expect(page.locator(".brand-text h1")).to_contain_text("GUMMI")
    expect(page.locator("#pane-portfolio")).to_have_class("tab-pane active")

    # 2. Test Navigation: Switch to Devices Explorer
    btn_devices = page.locator('.nav-tab[data-tab="devices"]')
    btn_devices.click()

    expect(page.locator("#pane-devices")).to_have_class("tab-pane active")
    expect(page.locator("#pane-portfolio")).not_to_have_class("active")

    # 3. Assert Devices table rows loaded
    device_rows = page.locator("#devices-table-body tr")
    page.wait_for_function('document.querySelectorAll("#devices-table-body tr").length > 0')
    count = device_rows.count()
    assert count > 1, f"Expected devices in table, got {count}"

    # 4. Test Device Inspection -> Device Properties
    # Find Inspect button for AHU-22 (or first device)
    inspect_btn = page.locator('#devices-table-body button:has-text("Inspect")').first
    inspect_btn.click()

    expect(page.locator("#pane-device-detail")).to_have_class("tab-pane active")
    expect(page.locator("#detail-device-title")).not_to_have_text("Select a Device")

    # 5. Verify Message Lifecycle Section (Model -> Discovery -> Proposal)
    lifecycle_container = page.locator("#detail-messages-timeline")
    page.wait_for_function('document.querySelectorAll("#detail-messages-timeline div").length > 0')
    expect(lifecycle_container).to_contain_text("MODEL")

    # 6. Test Navigation to Configuration & Rollout Tabs
    page.locator('.nav-tab[data-tab="config"]').click()
    expect(page.locator("#pane-config")).to_have_class("tab-pane active")

    page.locator('.nav-tab[data-tab="rollout"]').click()
    expect(page.locator("#pane-rollout")).to_have_class("tab-pane active")

    page.locator('.nav-tab[data-tab="admin"]').click()
    expect(page.locator("#pane-admin")).to_have_class("tab-pane active")

    # Final check that no JS exceptions were thrown during any tab switches or clicks
    assert len(page_errors) == 0, f"JavaScript errors detected during interaction: {page_errors}"
    page.close()


def test_gummi_device_filtering_and_pagination(gummi_server_url: str, browser_context: Browser):
    """Tests device search filtering and pagination controls in browser."""
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    page.goto(gummi_server_url)
    page.locator('.nav-tab[data-tab="devices"]').click()

    # Search for AHU-
    page.fill("#filter-search", "AHU")
    page.click("#btn-apply-filters")

    page.wait_for_timeout(300)
    device_rows = page.locator("#devices-table-body tr")
    assert device_rows.count() >= 1

    first_text = device_rows.first.inner_text()
    assert "AHU" in first_text

    # Clear filter
    page.click("#btn-clear-filters")
    page.wait_for_timeout(300)

    assert len(page_errors) == 0, f"JavaScript errors during filtering: {page_errors}"
    page.close()
