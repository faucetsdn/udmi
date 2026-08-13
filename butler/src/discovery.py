from src.uufi_client import UufiClient
from datetime import datetime, timezone

import sys
def run_discovery(conn_spec, registry_id, device_id, target_families, site_model=None):
    client = UufiClient(conn_spec, registry_id, site_model)
    print("Connecting UUFI...", file=sys.stderr); client.connect()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    
    families_dict = {}
    if target_families:
        for f in target_families:
            families_dict[f] = {"generation": now}
    else:
        families_dict = {"vendor": {"generation": now}, "bacnet": {"generation": now}, "ipv4": {"generation": now}}
        
    client.publish_discovery_config(device_id, now, families_dict)
    client.disconnect()
