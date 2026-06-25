import glob
import os
import re
from typing import Any, Dict, List, Tuple, Optional
from engine.models import TriageFailure

class UDMILogResolver:
    """
    Unified domain resolver for UDMI system logs, directories, and sharded components.
    Ensures strict DRY compliance by serving as the single point of log locating.
    """

    def __init__(self, udmi_root: str):
        self.udmi_root = os.path.abspath(udmi_root)

    def discover_active_site_name(self, run_dir: Optional[str] = None) -> str:
        """Dynamically discovers the active site model folder name under sites/."""
        try:
            base_dir = run_dir if run_dir else self.udmi_root
            sites_dir = os.path.join(base_dir, "sites")
            if os.path.exists(sites_dir):
                dirs = [d for d in os.listdir(sites_dir) if os.path.isdir(os.path.join(sites_dir, d))]
                non_default = [d for d in dirs if d != "udmi_site_model" and not re.match(r'^udmi_site_model_\d+$', d)]
                if non_default:
                    # Strip shard suffix (e.g., ZZ-TRI-FECTA_3 -> ZZ-TRI-FECTA)
                    base_name = re.sub(r'_\d+$', '', non_default[0])
                    return base_name
        except Exception:
            pass
        return "udmi_site_model"

    def discover_device_id(self, run_dir: str, test_id: Optional[str] = None) -> str:
        """Discovers the device under test by scanning the run/out/devices or run/devices folder."""
        devices_dir = os.path.join(run_dir, "out", "devices")
        if not os.path.exists(devices_dir):
            devices_dir = os.path.join(run_dir, "devices")
        if not os.path.exists(devices_dir):
            return "AHU-1"

        if test_id:
            for dev in os.listdir(devices_dir):
                dev_path = os.path.join(devices_dir, dev)
                if os.path.isdir(dev_path):
                    test_path = os.path.join(dev_path, "tests", test_id)
                    if os.path.exists(test_path):
                        return dev

        dirs = [d for d in os.listdir(devices_dir) if
                os.path.isdir(os.path.join(devices_dir, d))]
        if dirs:
            return dirs[0]
        return "AHU-1"

    def resolve_sharded_itemized_logs(
        self, 
        run_dir: str, 
        test_name: str, 
        occurrence_idx: int, 
        device_id: Optional[str] = None
    ) -> Tuple[str, str, str, str, str]:
        """
        Locates exact sharded logs for a test case from itemized sequencer outputs.
        Returns: (sequence_log_path, pubber_log_path, udmis_log_path, resolved_device_id, pubber_opts) relative to self.udmi_root.
        """
        itemized_out = os.path.join(run_dir, "test_itemized.out")
        if not os.path.exists(itemized_out):
            itemized_out = os.path.join(run_dir, "out/test_itemized.out")
            
        prefix = ""
        if os.path.exists(itemized_out):
            curr_idx = 0
            with open(itemized_out, 'r', encoding='utf-8', errors='replace') as f:
                for line in f:
                    tokens = line.split()
                    if tokens and tokens[0].isdigit():
                        if len(tokens) > 4 and tokens[4] == test_name:
                            if curr_idx == occurrence_idx:
                                prefix = tokens[0]
                                break
                            curr_idx += 1

        resolved_device_id = device_id if device_id else "AHU-1"
        pubber_opts = ""
        if prefix:
            prefix_int = int(prefix)
            itemized_in = os.path.join(self.udmi_root, "etc/test_itemized.in")
            if os.path.exists(itemized_in):
                with open(itemized_in, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                current_device = "AHU-1"
                for i, line in enumerate(lines, start=1):
                    tokens = line.strip().split()
                    if not tokens:
                        continue
                    if tokens[0] == "WITH":
                        if len(tokens) > 1:
                            current_device = tokens[1]
                    if i == prefix_int:
                        resolved_device_id = current_device
                        if tokens[0] == "TEST" and len(tokens) > 2:
                            pubber_opts = " ".join(tokens[2:])
                        break

        sharded_out_dirs = glob.glob(os.path.join(run_dir, "out_*"))
        if not sharded_out_dirs:
            # Fallback to checking self.udmi_root out_* directories
            sharded_out_dirs = glob.glob(os.path.join(self.udmi_root, "out_*"))

        dev_pattern = device_id if device_id else "*"
        
        # Singletone run fallback
        if not sharded_out_dirs or not prefix:
            active_site = self.discover_active_site_name()
            site_path = os.path.join(run_dir, "sites", active_site)
            if not os.path.exists(site_path):
                site_path = os.path.join(self.udmi_root, "sites", active_site)

            seq_glob = glob.glob(os.path.join(site_path, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
            seq_log = os.path.relpath(seq_glob[0], self.udmi_root) if seq_glob else ""

            pub_log = ""
            for name in ["pubber.log.combined", "pubber.log"]:
                for search_dir in [os.path.join(run_dir, "out"), run_dir, os.path.join(self.udmi_root, "out")]:
                    p = os.path.join(search_dir, name)
                    if os.path.exists(p):
                        pub_log = os.path.relpath(p, self.udmi_root)
                        break
                if pub_log:
                    break

            udm_log = ""
            for name in ["udmis.log.combined", "udmis.log"]:
                for search_dir in [os.path.join(run_dir, "out"), run_dir, os.path.join(self.udmi_root, "out")]:
                    p = os.path.join(search_dir, name)
                    if os.path.exists(p):
                        udm_log = os.path.relpath(p, self.udmi_root)
                        break
                if udm_log:
                    break

            return seq_log, pub_log, udm_log, resolved_device_id, pubber_opts

        # Sharded path resolution
        for sod in sorted(sharded_out_dirs):
            suffix = os.path.basename(sod).replace("out_", "")
            path_seq = os.path.join(sod, f"sequencer.log-{prefix}")
            if os.path.exists(path_seq):
                active_site = self.discover_active_site_name()
                
                # Look for sharded site directory inside run_dir first, then udmi_root
                shard_site = ""
                for base_dir in [run_dir, self.udmi_root]:
                    for candidate in [f"sites/udmi_site_model_{suffix}", f"sites/{active_site}_{suffix}", f"sites/{active_site}"]:
                        p = os.path.join(base_dir, candidate)
                        if os.path.exists(p):
                            shard_site = os.path.relpath(p, self.udmi_root)
                            break
                    if shard_site:
                        break

                seq_glob = glob.glob(os.path.join(self.udmi_root, shard_site, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True) if shard_site else []
                resolved_seq = os.path.relpath(seq_glob[0], self.udmi_root) if seq_glob else os.path.relpath(path_seq, self.udmi_root)

                pub_log = os.path.join(sod, f"pubber.log-{prefix}")
                if not os.path.exists(pub_log):
                    pub_log = os.path.join(sod, "pubber.log")

                udm_log = ""
                for name in ["udmis.log.combined", "udmis.log"]:
                    p = os.path.join(sod, name)
                    if os.path.exists(p):
                        udm_log = p
                        break

                return (
                    resolved_seq, 
                    os.path.relpath(pub_log, self.udmi_root) if os.path.exists(pub_log) else "", 
                    os.path.relpath(udm_log, self.udmi_root) if udm_log else "",
                    resolved_device_id,
                    pubber_opts
                )

        return "", "", "", "", ""

    def resolve_sharded_sequencer_logs(
        self, 
        run_dir: str, 
        test_name: str, 
        device_id: Optional[str] = None,
        expect_fail: bool = True
    ) -> Tuple[str, str, str]:
        """
        Locates sharded sequencer logs inside a specific run directory execution.
        Returns: (sequence_log_path, pubber_log_path, udmis_log_path) relative to self.udmi_root.
        """
        sharded_site_dirs = glob.glob(os.path.join(run_dir, "sites/udmi_site_model_*"))
        if not sharded_site_dirs:
            sharded_site_dirs = glob.glob(os.path.join(self.udmi_root, "sites/udmi_site_model_*"))

        active_site = self.discover_active_site_name()
        dev_pattern = device_id if device_id else "*"

        if not sharded_site_dirs:
            # Single run mode
            site_path = os.path.join(run_dir, "sites", active_site)
            if not os.path.exists(site_path):
                site_path = os.path.join(self.udmi_root, "sites", active_site)

            seq_glob = glob.glob(os.path.join(site_path, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
            seq_log = os.path.relpath(seq_glob[0], self.udmi_root) if seq_glob else ""

            pub_log = ""
            for name in ["pubber.log.combined", "pubber.log"]:
                for search_dir in [os.path.join(run_dir, "out"), run_dir, os.path.join(self.udmi_root, "out")]:
                    p = os.path.join(search_dir, name)
                    if os.path.exists(p):
                        pub_log = os.path.relpath(p, self.udmi_root)
                        break
                if pub_log:
                    break

            udm_log = ""
            for name in ["udmis.log.combined", "udmis.log"]:
                for search_dir in [os.path.join(run_dir, "out"), run_dir, os.path.join(self.udmi_root, "out")]:
                    p = os.path.join(search_dir, name)
                    if os.path.exists(p):
                        udm_log = os.path.relpath(p, self.udmi_root)
                        break
                if udm_log:
                    break

            return seq_log, pub_log, udm_log

        # Sharded sequencer resolution
        for ssd in sorted(sharded_site_dirs):
            suffix = os.path.basename(ssd).replace("udmi_site_model_", "")
            seq_glob = glob.glob(os.path.join(ssd, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
            if seq_glob:
                seq_log = seq_glob[0]
                try:
                    with open(seq_log, 'r', encoding='utf-8', errors='replace') as f:
                        content = f.read()
                    has_fail = "RESULT fail" in content or "Failed waiting" in content
                    if (expect_fail and has_fail) or (not expect_fail and not has_fail):
                        shard_out = f"out_{suffix}"
                        
                        # Look for shard_out folder inside run_dir first
                        shard_out_path = os.path.join(run_dir, shard_out)
                        if not os.path.exists(shard_out_path):
                            shard_out_path = os.path.join(self.udmi_root, shard_out)

                        pub_log = ""
                        for name in ["pubber.log.combined", "pubber.log"]:
                            p = os.path.join(shard_out_path, name)
                            if os.path.exists(p):
                                pub_log = os.path.relpath(p, self.udmi_root)
                                break

                        udm_log = ""
                        for name in ["udmis.log.combined", "udmis.log"]:
                            p = os.path.join(shard_out_path, name)
                            if os.path.exists(p):
                                udm_log = os.path.relpath(p, self.udmi_root)
                                break

                        return os.path.relpath(seq_log, self.udmi_root), pub_log, udm_log
                except Exception:
                    pass

        return "", "", ""


class UDMIResultParser:
    """
    Robust parsing engine to ingest, normalize, and classify test outcomes 
    from output streams or output logs.
    """

    @staticmethod
    def normalize_reason(text: str) -> str:
        """Normalizes dynamic variable variables (timestamps, hashes) from error reasons."""
        if not text:
            return ""
        # Redact absolute ISO timestamps
        text = re.sub(r'\b202\d-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z\b', 'TIMESTAMP', text)
        text = re.sub(r'202\d-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', 'TIMESTAMP', text)
        # Redact event pipeline details
        text = re.sub(r'(Pipeline type event error: While processing message ).*', r'\1REDACTED', text)
        return text

    def parse_results_file(self, filepath: str, is_itemized: bool = False) -> List[TriageFailure]:
        """
        Parses a sequencer.out or test_itemized.out file, returning list of TriageFailure dataclasses.
        """
        failures = []
        if not os.path.exists(filepath):
            return failures

        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                
                tokens = line.split()
                if is_itemized:
                    if tokens and (tokens[0].isdigit() or re.match(r'^\d+$', tokens[0])):
                        tokens = tokens[1:]

                if len(tokens) < 6:
                    continue

                outcome = tokens[1]
                if outcome == "fail":
                    test_name = tokens[3]
                    category = tokens[2]
                    if category == "schemas":
                        continue
                    reason = self.normalize_reason(" ".join(tokens[6:])) if len(tokens) > 6 else ""
                    
                    failures.append(TriageFailure(
                        test_name=test_name,
                        category=category,
                        suite="itemized" if is_itemized else "sequencer",
                        metadata={"raw_reason": reason}
                    ))
        return failures
