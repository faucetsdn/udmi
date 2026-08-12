import os
import re
from typing import Dict, List, Optional, Set, Tuple


class UDMIEntityExtractor:
    """
    Intelligently extracts UDMI domain entities (site model, device ID, test name)
    from natural language user queries, commands, and chat input.
    """

    def __init__(self, udmi_root: str):
        self.udmi_root = os.path.abspath(udmi_root)
        self._known_sites: Set[str] = set()
        self._known_tests: Set[str] = set()
        self._init_known_entities()

    def _init_known_entities(self):
        """Discovers known site names and sequence test cases from the workspace."""
        # 1. Discover sites
        sites_dir = os.path.join(self.udmi_root, "sites")
        if os.path.exists(sites_dir):
            for s in os.listdir(sites_dir):
                if os.path.isdir(os.path.join(sites_dir, s)) and not s.startswith("."):
                    self._known_sites.add(s)

        # 2. Discover tests from Java sequence definitions
        sequences_dir = os.path.join(
            self.udmi_root,
            "validator/src/main/java/com/google/daq/mqtt/sequencer/sequences"
        )
        if os.path.exists(sequences_dir):
            for fname in os.listdir(sequences_dir):
                if fname.endswith(".java"):
                    fpath = os.path.join(sequences_dir, fname)
                    try:
                        with open(fpath, "r", encoding="utf-8") as f:
                            content = f.read()
                            # Match @Test public void test_name() or public void test_name()
                            matches = re.findall(r'@Test\s+(?:public\s+)?void\s+([a-zA-Z0-9_]+)\s*\(', content)
                            for m in matches:
                                self._known_tests.add(m)
                    except Exception:
                        pass

        # 3. Discover parameterized sequence tests from test output directories
        sites_dir = os.path.join(self.udmi_root, "sites")
        if os.path.exists(sites_dir):
            for site_dir in os.listdir(sites_dir):
                for sub in ("", "udmi"):
                    devices_dir = os.path.join(sites_dir, site_dir, sub, "out", "devices")
                    if os.path.exists(devices_dir):
                        for dev in os.listdir(devices_dir):
                            tests_dir = os.path.join(devices_dir, dev, "tests")
                            if os.path.exists(tests_dir):
                                for tst in os.listdir(tests_dir):
                                    if not tst.startswith("."):
                                        self._known_tests.add(tst)

    def extract_entities(
        self,
        text: str,
        current_site: Optional[str] = None
    ) -> Dict[str, Optional[str]]:
        """
        Extracts (site_model, device_id, test_id) from the input text.
        """
        result: Dict[str, Optional[str]] = {
            "site_model": None,
            "device_id": None,
            "test_id": None
        }

        if not text:
            return result

        # 1. Extract Site Model
        # Pattern match "sites/<site_name>"
        site_path_match = re.search(r'sites/([a-zA-Z0-9_\-]+)', text)
        if site_path_match:
            result["site_model"] = f"sites/{site_path_match.group(1)}"
        else:
            for s in sorted(self._known_sites, key=len, reverse=True):
                if re.search(rf'\b{re.escape(s)}\b', text, re.IGNORECASE):
                    result["site_model"] = f"sites/{s}"
                    break

        resolved_site = result["site_model"] or current_site

        # 2. Extract Device ID
        known_devices: Set[str] = set()
        if resolved_site:
            clean_s = resolved_site.replace("sites/", "").strip("/")
            for sub in (clean_s, os.path.join(clean_s, "udmi")):
                dev_dir = os.path.join(self.udmi_root, "sites", sub, "devices")
                if os.path.exists(dev_dir):
                    for d in os.listdir(dev_dir):
                        if os.path.isdir(os.path.join(dev_dir, d)):
                            known_devices.add(d)

        # Search for known devices in query
        for dev in sorted(known_devices, key=len, reverse=True):
            if re.search(rf'\b{re.escape(dev)}\b', text, re.IGNORECASE):
                result["device_id"] = dev
                break

        # Fallback to standard UDMI device naming regex (e.g. AHU-1, EM-11, CGW-501, DDC-7, TEST-181)
        if not result["device_id"]:
            dev_regex_match = re.search(r'\b([A-Z0-9]{2,10}-[A-Z0-9]{1,10})\b', text)
            if dev_regex_match:
                result["device_id"] = dev_regex_match.group(1)

        # 3. Extract Test ID (checking longer/parameterized tests first, e.g. +bacnet before base)
        for tst in sorted(self._known_tests, key=len, reverse=True):
            if re.search(rf'(?:\b|(?<=\s)){re.escape(tst)}(?:\b|(?=\s)|$)', text, re.IGNORECASE):
                result["test_id"] = tst
                break

        # Fallback regex for snake_case test names with optional +family (e.g. pointset_publish, scan_periodic_now_enumerate+bacnet)
        if not result["test_id"]:
            test_regex_match = re.search(r'\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+(?:\+[a-z0-9]+)?)\b', text)
            if test_regex_match:
                candidate = test_regex_match.group(1)
                # Filter common non-test snake_case phrases
                if candidate not in ("udmi_site_model", "pointset_publish_cloud", "test_runs", "test_id", "device_id"):
                    result["test_id"] = candidate

        return result

