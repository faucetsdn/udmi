"""End-to-End browser UI automation test suite using Playwright."""

import os
import re
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

    server = GummiServer(host="127.0.0.1", port=port, mock_mode=True)
    server_address = (server.host, server.port)
    httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
    httpd.daemon_threads = True
    httpd.db = server.db
    httpd.uufi = server.uufi
    httpd.console = server.console
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
    last_seen_cell = page.locator("#devices-table-body tr td code").first
    expect(last_seen_cell).to_have_text(re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"))

    # 4. Test Device Inspection -> Device Properties
    # Find Inspect button for AHU-22 (or first device)
    inspect_btn = page.locator('#devices-table-body button:has-text("Inspect")').first
    inspect_btn.click()

    expect(page.locator("#pane-device-detail")).to_have_class("tab-pane active")
    expect(page.locator("#detail-device-title")).not_to_have_text("Select a Device")
    expect(page.locator("#detail-lastseen")).to_have_text(re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"))

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


def test_gummi_jetski_task_console(gummi_server_url: str, browser_context: Browser):
    """Verifies embedded Jetski task console creation, xterm rendering, expand/collapse, and input."""
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    page.goto(gummi_server_url)
    page.wait_for_load_state("domcontentloaded")

    # 1. Verify "jetski!" button is visible in top header
    btn_jetski = page.locator("#btn-jetski")
    expect(btn_jetski).to_be_visible()
    expect(btn_jetski).to_contain_text("jetski!")

    # Console pane should initially be hidden
    console_pane = page.locator("#console-pane")
    expect(console_pane).to_be_hidden()

    # 2. Click "jetski!" button to open embedded console window
    btn_jetski.click()

    expect(console_pane).to_be_visible()
    expect(page.locator("#console-session-badge")).to_contain_text("gummi~agent")
    expect(page.locator(".console-title-area h3")).to_contain_text("Jetski Task Console")

    # Verify console pane and terminal container background color is white
    bg_pane = page.evaluate("() => window.getComputedStyle(document.getElementById('console-pane')).backgroundColor")
    assert bg_pane in ("rgb(255, 255, 255)", "#ffffff", "white"), f"Expected white background for console pane, got {bg_pane}"
    bg_term = page.evaluate("() => window.getComputedStyle(document.getElementById('terminal-container')).backgroundColor")
    assert bg_term in ("rgb(255, 255, 255)", "#ffffff", "white"), f"Expected white background for terminal container, got {bg_term}"

    # 3. Verify terminal container contains initialized xterm DOM
    page.wait_for_function('document.querySelectorAll("#terminal-container .xterm").length > 0')
    xterm_el = page.locator("#terminal-container .xterm")
    expect(xterm_el).to_be_visible()

    # 4. Test Expand and Restore controls
    btn_expand = page.locator("#btn-expand-console")
    btn_expand.click()
    expect(console_pane).to_have_class(re.compile(r"\bexpanded\b"))
    expect(btn_expand).to_have_text("Restore")

    btn_expand.click()
    expect(console_pane).not_to_have_class(re.compile(r"\bexpanded\b"))
    expect(btn_expand).to_have_text("Expand")

    # 5. Type input into terminal
    page.locator("#terminal-container .xterm-helper-textarea").focus()
    page.keyboard.type("echo hello\n")

    # 6. Test Close console
    btn_close = page.locator("#btn-close-console")
    btn_close.click()
    expect(console_pane).to_be_hidden()

    # 7. Test clicking GUMMI brand header does NOT open console
    brand_header = page.locator(".header-brand")
    brand_header.click()
    expect(console_pane).to_be_hidden()

    # 8. Test clicking btn-jetski opens console again
    btn_jetski.click()
    expect(console_pane).to_be_visible()

    # 9. Test diagnostics indicator rendering via term-log polling
    import json
    page.route("**/api/project/term-log*", lambda route: route.fulfill(
        status=200,
        content_type="application/json",
        body=json.dumps({
            "data": "",
            "offset": 100,
            "cleared": False,
            "running": True,
            "diagnostics": {
                "state": "auth_required",
                "status_text": "Auth Required (run 'glogin')",
                "severity": "warning",
                "alert": "Authentication Required: Google ThinMint certificate expired. Run 'glogin' to authenticate."
            }
        })
    ))
    expect(page.locator("#console-status-text")).to_have_text("Auth Required (run 'glogin')")
    expect(page.locator("#console-status-text")).to_have_class(re.compile(r"\bstatus-warning\b"))
    expect(page.locator("#console-alert-banner")).to_be_visible()
    expect(page.locator("#console-alert-msg")).to_contain_text("Authentication Required")
    expect(page.locator("#btn-alert-restart")).to_be_visible()

    assert len(page_errors) == 0, f"JavaScript errors during console interactions: {page_errors}"
    page.close()


def test_gummi_jetski_button_color_coding(gummi_server_url: str, browser_context: Browser):
    """Verifies color coding on #btn-jetski:
    Blue == not running, no error
    Red == not running, error
    Yellow == actively doing something
    Green == running, idle
    """
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    page.goto(gummi_server_url)
    page.wait_for_load_state("domcontentloaded")

    btn = page.locator("#btn-jetski")
    expect(btn).to_be_visible()

    # 1. Blue: Not running, no error
    page.evaluate("() => window.updateJetskiButtonState('blue')")
    expect(btn).to_have_attribute("data-color", "blue")
    expect(btn).to_have_class(re.compile(r"\bstate-blue\b"))
    expect(btn).to_have_class(re.compile(r"\bstate-not-running\b"))

    # 2. Red: Not running, error
    page.evaluate("() => window.updateJetskiButtonState('red')")
    expect(btn).to_have_attribute("data-color", "red")
    expect(btn).to_have_class(re.compile(r"\bstate-red\b"))
    expect(btn).to_have_class(re.compile(r"\bstate-error\b"))

    # 3. Yellow: Actively doing something
    page.evaluate("() => window.updateJetskiButtonState('yellow')")
    expect(btn).to_have_attribute("data-color", "yellow")
    expect(btn).to_have_class(re.compile(r"\bstate-yellow\b"))
    expect(btn).to_have_class(re.compile(r"\bstate-active\b"))

    # 4. Green: Running, idle
    page.evaluate("() => window.updateJetskiButtonState('green')")
    expect(btn).to_have_attribute("data-color", "green")
    expect(btn).to_have_class(re.compile(r"\bstate-green\b"))
    expect(btn).to_have_class(re.compile(r"\bstate-idle\b"))

    # 5. Verify updateDiagnosticsUI sets the proper button state automatically
    # 5a. Not running, no error -> blue
    page.evaluate("() => window.updateDiagnosticsUI({ running: false, severity: 'neutral' })")
    expect(btn).to_have_attribute("data-color", "blue")

    # 5b. Not running, error -> red
    page.evaluate("() => window.updateDiagnosticsUI({ running: false, severity: 'error', exit_code: 1 })")
    expect(btn).to_have_attribute("data-color", "red")

    # 5c. Running, active -> yellow
    page.evaluate("() => window.updateDiagnosticsUI({ running: true, active: true, state: 'active' })")
    expect(btn).to_have_attribute("data-color", "yellow")

    # 5d. Running, idle -> green
    page.evaluate("() => window.updateDiagnosticsUI({ running: true, active: false, state: 'idle' })")
    expect(btn).to_have_attribute("data-color", "green")

    assert len(page_errors) == 0, f"JavaScript errors during button color test: {page_errors}"
    page.close()


def test_gummi_console_portrait_mode_height(gummi_server_url: str, browser_context: Browser):
    """Verifies that in portrait mode (height > width), console pane takes up 1/2 of vertical space."""
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    # Set portrait viewport: width 600, height 1000
    page.set_viewport_size({"width": 600, "height": 1000})
    page.goto(gummi_server_url)
    page.wait_for_load_state("domcontentloaded")

    # Open console
    btn_jetski = page.locator("#btn-jetski")
    btn_jetski.click()

    console_pane = page.locator("#console-pane")
    expect(console_pane).to_be_visible()

    # Measure computed height in portrait mode
    pane_height = page.evaluate("() => document.getElementById('console-pane').getBoundingClientRect().height")
    window_height = page.evaluate("() => window.innerHeight")
    # In portrait mode (1000px height), console should take up 1/2 = 500px
    assert abs(pane_height - (window_height / 2)) <= 2, f"Expected 50vh height (~{window_height / 2}px), got {pane_height}px"

    # Switch to landscape: width 1200, height 800
    page.set_viewport_size({"width": 1200, "height": 800})
    time.sleep(0.2)
    pane_height_landscape = page.evaluate("() => document.getElementById('console-pane').getBoundingClientRect().height")
    # In landscape mode, height should be standard fixed 380px
    assert abs(pane_height_landscape - 380) <= 2, f"Expected 380px height in landscape, got {pane_height_landscape}px"

    assert len(page_errors) == 0, f"JavaScript errors during portrait mode test: {page_errors}"
    page.close()


def test_gummi_etcd_explorer_full_workflow(gummi_server_url: str, browser_context: Browser):
    """Verifies the ETCD Explorer UI, 3-column drill-down, bound device navigation, copy button, and hash deep-linking."""
    page: Page = browser_context.new_page()
    page_errors: List[str] = []
    page.on("pageerror", lambda err: page_errors.append(str(err)))

    # 1. Load root page and navigate to Explorer tab
    page.goto(gummi_server_url)
    page.wait_for_load_state("domcontentloaded")
    assert len(page_errors) == 0, f"JavaScript errors detected on load: {page_errors}"

    btn_explorer = page.locator('.nav-tab[data-tab="explorer"]')
    btn_explorer.click()

    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bactive\b"))
    expect(page.locator("#pane-portfolio")).not_to_have_class("active")
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bdark-theme\b"))

    # Test Theme Toggle
    theme_btn = page.locator("#theme-toggle-explorer")
    theme_btn.click()
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\blight-theme\b"))
    theme_btn.click()
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bdark-theme\b"))

    # 2. Assert Column 1 registries loaded
    page.wait_for_function('document.querySelectorAll("#explorer-registries-list .explorer-item").length > 0')
    regs = page.locator("#explorer-registries-list .explorer-item")
    assert regs.count() >= 1

    # Test registry search filter
    search_reg = page.locator("#search-explorer-registries")
    search_reg.fill("TRI")
    time.sleep(0.3)
    expect(page.locator('#explorer-registries-list .explorer-item:has-text("ZZ-TRI-FECTA")')).to_be_visible()
    search_reg.fill("")
    time.sleep(0.3)

    # 3. Select registry 'ZZ-TRI-FECTA'
    tri_reg = page.locator('#explorer-registries-list .explorer-item:has-text("ZZ-TRI-FECTA")')
    tri_reg.click()
    expect(tri_reg).to_have_class(re.compile(r"\bactive\b"))
    expect(page.locator("#explorer-active-registry-label")).to_have_text("ZZ-TRI-FECTA")

    # 4. Assert Column 2 devices loaded for ZZ-TRI-FECTA
    page.wait_for_function('document.querySelectorAll("#explorer-devices-list .explorer-item").length > 0')
    devs = page.locator("#explorer-devices-list .explorer-item")
    assert devs.count() >= 2

    # Test device search filter
    search_dev = page.locator("#search-explorer-devices")
    search_dev.fill("AHU-1")
    time.sleep(0.3)
    expect(page.locator('#explorer-devices-list .explorer-item:has-text("AHU-1")')).to_be_visible()
    search_dev.fill("")
    time.sleep(0.3)

    # 5. Select device 'AHU-1'
    ahu1_dev = page.locator('#explorer-devices-list').get_by_text("AHU-1", exact=True)
    ahu1_dev.click()
    expect(ahu1_dev).to_have_class(re.compile(r"\bactive\b"))
    expect(page.locator("#explorer-active-device-label")).to_have_text("ZZ-TRI-FECTA / AHU-1")

    # 6. Assert Column 3 properties loaded
    page.wait_for_function('document.querySelectorAll("#explorer-properties-content .properties-table").length > 0')
    expect(page.locator('.property-group-title:has-text("Device Properties")')).to_be_visible()
    expect(page.locator('.property-group-title:has-text("Collections")')).to_be_visible()

    # Check JSON container and copy button for :config
    config_row = page.locator('.property-row[data-key=":config"]')
    expect(config_row).to_be_visible()
    copy_btn = config_row.locator(".copy-btn")
    expect(copy_btn).to_have_text("📋 Copy")

    # Check bound device link
    bound_link = page.locator('.property-row[data-key="/c/bound_devices:AHU-2"] a.device-link')
    expect(bound_link).to_have_text("AHU-2")

    # 7. Click bound device link AHU-2 -> verifies 1-click cross-navigation
    bound_link.click()
    expect(page.locator("#explorer-active-device-label")).to_have_text("ZZ-TRI-FECTA / AHU-2")
    ahu2_dev = page.locator('#explorer-devices-list').get_by_text("AHU-2", exact=True)
    expect(ahu2_dev).to_have_class(re.compile(r"\bactive\b"))

    # 8. Test URL Hash deep linking (#explorer/ZZ-TRI-FECTA/AHU-1/:config)
    page.goto(f"{gummi_server_url}/#explorer/ZZ-TRI-FECTA/AHU-1/%3Aconfig")
    page.wait_for_load_state("domcontentloaded")
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bactive\b"))
    expect(page.locator("#explorer-active-device-label")).to_have_text("ZZ-TRI-FECTA / AHU-1")
    expect(page.locator('.property-row[data-key=":config"]')).to_have_class(re.compile(r"\bhighlighted\b"))

    # 9. Test Legacy URL Hash deep linking (#/ZZ-TRI-FECTA/AHU-2)
    page.goto(f"{gummi_server_url}/#/ZZ-TRI-FECTA/AHU-2")
    page.wait_for_load_state("domcontentloaded")
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bactive\b"))
    expect(page.locator("#explorer-active-device-label")).to_have_text("ZZ-TRI-FECTA / AHU-2")

    # 10. Test cross-navigation from Devices Explorer table and Device Properties pane
    # Navigate to Devices tab
    page.locator('.nav-tab[data-tab="devices"]').click()
    expect(page.locator("#pane-devices")).to_have_class("tab-pane active")
    page.wait_for_function('document.querySelectorAll("#devices-table-body tr").length > 0')

    # Click ETCD button in row
    etcd_btn = page.locator('#devices-table-body button:has-text("🗄️ ETCD")').first
    etcd_btn.click()
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bactive\b"))

    # Navigate to Device Properties tab and click Raw ETCD button
    page.locator('.nav-tab[data-tab="device-detail"]').click()
    expect(page.locator("#pane-device-detail")).to_have_class("tab-pane active")
    page.locator("#btn-view-raw-etcd").click()
    expect(page.locator("#pane-explorer")).to_have_class(re.compile(r"\bactive\b"))

    assert len(page_errors) == 0, f"JavaScript errors encountered during explorer test: {page_errors}"
    page.close()


