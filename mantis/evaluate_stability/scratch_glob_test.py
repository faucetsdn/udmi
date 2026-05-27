import glob
import os
import re
from mantis.evaluate_stability.analyzer import TestResult, RunAnalyzer

UDMI_ROOT = "/usr/local/google/home/heykhyati/Projects/udmi_clone/udmi"
analyzer = RunAnalyzer(udmi_root=UDMI_ROOT)

site_run_path = "/usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/out/test_bundles/udmi-support_20260430-132704 (1)_20260526_140612/run_1/sites/bos-platform-staging"

log_files = glob.glob(os.path.join(site_run_path, "**/sequence*.log"), recursive=True)
print(f"Found {len(log_files)} log files.")

count = 0
for lf in log_files:
    try:
        device_id = "AHU-1"
        parts = lf.split("devices/")
        if len(parts) > 1:
            device_id = parts[1].split("/")[0]
            
        with open(lf, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()
            for line in reversed(lines[-5:]):
                if "RESULT" in line or "CPBLTY" in line:
                    m = re.search(r'(RESULT|CPBLTY)\s+.*', line)
                    if m:
                        raw_line = m.group(0)
                        tokens = raw_line.split()
                        if tokens and tokens[0].isdigit():
                            tokens = tokens[1:]
                        test_name = tokens[3] if len(tokens) > 3 else ""
                        is_itemized = test_name in analyzer.itemized_baseline.expected
                        
                        try:
                            res = TestResult(raw_line, device_id=device_id, is_itemized=is_itemized)
                        except Exception as err:
                            if count < 5:
                                print(f"TestResult parser failed for line: '{raw_line}' -> Error: {err}")
                                count += 1
                            break
    except Exception as e:
        pass
