import copy
from datetime import datetime, timezone
import json
import os
import sys
import uuid
import psycopg2

try:
    from src.connection import ButlerConnection
except (ImportError, ModuleNotFoundError):
    from butler.src.connection import ButlerConnection

try:
    from udmi.common.db.postgres import PostgresManager
    from udmi.common.project_spec import parse_project_spec
except (ImportError, ModuleNotFoundError):
    PostgresManager = None
    parse_project_spec = None


def run_mapping(conn_spec, registry_id, site_model=None, target_families=None):
    pg_port = os.environ.get("POSTGRES_PORT")
    if not pg_port and conn_spec and parse_project_spec:
        spec_info = parse_project_spec(conn_spec)
        port = spec_info.get("port")
        if port and str(port) != "8883":
            pg_port = str(int(port) + 3)

    if PostgresManager:
        pg_mgr = PostgresManager(port=pg_port)
        try:
            conn = pg_mgr.get_connection()
        except psycopg2.Error as e:
            print(f"Error connecting to DB: {e}", file=sys.stderr)
            return
    else:
        try:
            conn = psycopg2.connect(
                host=os.environ.get("POSTGRES_HOST", "127.0.0.1"),
                port=pg_port or "5432",
                user=os.environ.get("POSTGRES_USER", "postgres"),
                dbname=os.environ.get("POSTGRES_DB", "postgres"),
            )
        except psycopg2.Error as e:
            print(f"Error connecting to DB: {e}", file=sys.stderr)
            return

    cursor = conn.cursor()

    # 1. Get most recent model for each device_id in this registry
    models = []
    if site_model:
        devices_dir = os.path.join(site_model, "devices")
        if os.path.exists(devices_dir):
            for device_id in os.listdir(devices_dir):
                meta_path = os.path.join(devices_dir, device_id, "metadata.json")
                if os.path.exists(meta_path):
                    try:
                        with open(meta_path, "r") as mf:
                            models.append((device_id, json.load(mf)))
                    except Exception as e:
                        print(f"err: {e}", file=sys.stderr)
    print(f"Found {len(models)} models", file=sys.stderr)

    # 2. Get all discovery events for this registry ordered chronologically
    cursor.execute("""
        SELECT payload, device_id
        FROM udmi_messages
        WHERE registry_id = %s AND sub_folder = 'discovery' AND sub_type = 'events'
        ORDER BY id ASC
    """, (registry_id,))
    
    discovery_events = cursor.fetchall()
    print(f"Found {len(discovery_events)} discovery events", file=sys.stderr)
    
    discovered_devices = []
    
    for row in discovery_events:
        payload = row[0]
        gateway_id = row[1]
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except:
                continue

        if isinstance(payload, dict) and "payload" in payload and isinstance(payload.get("payload"), dict):
            payload = payload["payload"]
        
        # Check root level primary family
        bacnet_addr = None
        ipv4_addr = None
        vendor_addr = None
        
        if payload.get('family') == 'bacnet':
            bacnet_addr = payload.get('addr')
        elif payload.get('family') == 'vendor':
            vendor_addr = payload.get('addr')

        # Check secondary families block
        families = payload.get('families', {})
        if 'bacnet' in families:
            bacnet_addr = bacnet_addr or families['bacnet'].get('addr')
        if 'ipv4' in families:
            ipv4_addr = families['ipv4'].get('addr')
        if 'vendor' in families:
            vendor_addr = vendor_addr or families['vendor'].get('addr')
            
        if bacnet_addr or ipv4_addr or vendor_addr:
            discovered_devices.append({
                'bacnet': str(bacnet_addr) if bacnet_addr else None,
                'ipv4': str(ipv4_addr) if ipv4_addr else None,
                'vendor': str(vendor_addr) if vendor_addr else None,
                'generation': payload.get('generation'),
                'gateway_id': gateway_id
            })
                
    modeled_devices = []
    
    original_models = {}
    for device_id, payload in models:
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except:
                continue
                
        original_models[device_id] = payload
        
        localnet = payload.get('localnet', {})
        families = localnet.get('families', {})
        bacnet_addr = families.get('bacnet', {}).get('addr') if 'bacnet' in families else None
        ipv4_addr = families.get('ipv4', {}).get('addr') if 'ipv4' in families else None
        vendor_addr = families.get('vendor', {}).get('addr') if 'vendor' in families else None
            
        modeled_devices.append({
            'device_id': device_id,
            'bacnet': str(bacnet_addr) if bacnet_addr else None,
            'ipv4': str(ipv4_addr) if ipv4_addr else None,
            'vendor': str(vendor_addr) if vendor_addr else None
        })
            
    # Combine everything using a union-find approach to group related addresses
    entities = []
    
    # 1. Initialize entities from models
    for m in modeled_devices:
        ent = { 'device_id': m['device_id'], 'bacnet': set(), 'ipv4': set(), 'vendor': set() }
        if m['bacnet']: ent['bacnet'].add(m['bacnet'])
        if m['ipv4']: ent['ipv4'].add(m['ipv4'])
        if m['vendor']: ent['vendor'].add(m['vendor'])
        entities.append(ent)
        
    # 2. Process discovery events and merge
    for d in discovered_devices:
        d_bacnet = d['bacnet']
        d_ipv4 = d['ipv4']
        d_vendor = d['vendor']
        
        matching_indices = []
        for idx, ent in enumerate(entities):
            if (d_bacnet and d_bacnet in ent['bacnet']) or \
               (d_ipv4 and d_ipv4 in ent['ipv4']) or \
               (d_vendor and d_vendor in ent['vendor']):
                matching_indices.append(idx)
                
        if not matching_indices:
            # Create a new entity for this unmatched discovery event
            new_ent = { 'device_id': None, 'bacnet': set(), 'ipv4': set(), 'vendor': set() }
            if d_bacnet: new_ent['bacnet'].add(d_bacnet)
            if d_ipv4: new_ent['ipv4'].add(d_ipv4)
            if d_vendor: new_ent['vendor'].add(d_vendor)
            entities.append(new_ent)
        else:
            # Merge the discovery addresses into the first matching entity
            primary = entities[matching_indices[0]]
            if d_bacnet: primary['bacnet'].add(d_bacnet)
            if d_ipv4: primary['ipv4'].add(d_ipv4)
            if d_vendor: primary['vendor'].add(d_vendor)
            
            # If the discovery event bridged multiple existing entities, merge them together
            for other_idx in reversed(matching_indices[1:]):
                other = entities.pop(other_idx)
                primary['bacnet'].update(other['bacnet'])
                primary['ipv4'].update(other['ipv4'])
                primary['vendor'].update(other['vendor'])
                
                # Combine device IDs if needed
                if not primary['device_id']:
                    primary['device_id'] = other['device_id']
                elif other['device_id'] and primary['device_id'] != other['device_id']:
                    primary['device_id'] = f"{primary['device_id']}+{other['device_id']}"
                    
    # 3. Generate Model Proposal Messages
    outputs = []
    unknown_counter = 1

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    for ent in entities:
        dev_id = ent['device_id']

        b_addr = sorted(ent['bacnet'])[0] if ent['bacnet'] else None
        i_addr = sorted(ent['ipv4'])[0] if ent['ipv4'] else None
        v_addr = sorted(ent['vendor'])[0] if ent['vendor'] else None

        if dev_id:
            orig = copy.deepcopy(original_models.get(dev_id, {}))
            orig['version'] = '1.5.7'
            orig['timestamp'] = now

            orig_localnet = orig.get('localnet', {}).get('families', {})
            orig_b = orig_localnet.get('bacnet', {}).get('addr') if 'bacnet' in orig_localnet else None
            orig_i = orig_localnet.get('ipv4', {}).get('addr') if 'ipv4' in orig_localnet else None
            orig_v = orig_localnet.get('vendor', {}).get('addr') if 'vendor' in orig_localnet else None

            diff_families = {}
            if b_addr and b_addr != orig_b:
                diff_families['bacnet'] = {'addr': b_addr}
            if i_addr and i_addr != orig_i:
                diff_families['ipv4'] = {'addr': i_addr}
            if v_addr and v_addr != orig_v:
                diff_families['vendor'] = {'addr': v_addr}

            if diff_families:
                if 'localnet' not in orig:
                    orig['localnet'] = {}
                if 'families' not in orig['localnet']:
                    orig['localnet']['families'] = {}
                orig['localnet']['families'].update(diff_families)

            # Check for extra metadata with new refs
            if site_model:
                for family in ['vendor', 'bacnet', 'ipv4']:
                    addr = None
                    if family == 'vendor' and v_addr: addr = v_addr
                    if family == 'bacnet' and b_addr: addr = b_addr
                    if family == 'ipv4' and i_addr: addr = i_addr

                    if not addr: continue

                    extra_meta_path = os.path.join(site_model, "extras", f"discovered_{family}-{addr}", "cloud_metadata", "udmi_discovered_with.json")
                    if os.path.exists(extra_meta_path):
                        try:
                            with open(extra_meta_path, "r") as emf:
                                extra_meta = json.load(emf)

                            refs = extra_meta.get("refs", {})
                            if refs:
                                if "pointset" not in orig:
                                    orig["pointset"] = {}
                                if "points" not in orig["pointset"]:
                                    orig["pointset"]["points"] = {}

                                points = orig["pointset"]["points"]
                                for ref_key, ref_val in refs.items():
                                    pt_name = ref_val.get("point", ref_key)
                                    if pt_name not in points:
                                        points[pt_name] = {}
                                    points[pt_name]["ref"] = ref_key
                        except Exception as e:
                            print(f"Error merging refs: {e}", file=sys.stderr)

            orig_timestamp = original_models.get(dev_id, {}).get("timestamp", "")
            if diff_families or site_model:
                outputs.append({'deviceId': dev_id, 'model': orig, 'updateFrom': orig_timestamp})
        else:
            # New device: create complete proposed model
            dev_id = f"UNK-{unknown_counter}"
            unknown_counter += 1

            new_families = {}
            if b_addr: new_families['bacnet'] = {'addr': b_addr}
            if i_addr: new_families['ipv4'] = {'addr': i_addr}
            if v_addr: new_families['vendor'] = {'addr': v_addr}

            proposed = {
                'version': '1.5.7',
                'timestamp': now,
                'localnet': {'families': new_families}
            }
            outputs.append({'deviceId': dev_id, 'model': proposed, 'updateFrom': ''})

    if site_model:
        # Write discovery extras
        extras_dir = os.path.join(site_model, "extras")
        os.makedirs(extras_dir, exist_ok=True)
        for d in discovered_devices:
            target_fams = target_families if target_families else ['vendor', 'bacnet', 'ipv4']
            for family in target_fams:
                addr = d.get(family)
                if addr:
                    dev_dir = os.path.join(extras_dir, f"discovered_{family}-{addr}")
                    os.makedirs(os.path.join(dev_dir, "cloud_metadata"), exist_ok=True)
                    
                    with open(os.path.join(dev_dir, "cloud_model.json"), "w") as f:
                        json.dump({
                            "resource_type": "PROXIED",
                            "gateway": {"gateway_id": d.get("gateway_id")}
                        }, f)
                        
                    with open(os.path.join(dev_dir, "cloud_metadata", "udmi_discovered_with.json"), "w") as f:
                        gen = d.get('generation')
                        if not gen:
                            gen = now
                            
                        json.dump({
                            "addr": str(addr),
                            "generation": gen
                        }, f)

        # Write mapped devices to devices dir
        for out in outputs:
            dev_id = out["deviceId"]
            dev_dir = os.path.join(site_model, "devices", dev_id)
            os.makedirs(dev_dir, exist_ok=True)
            meta_path = os.path.join(dev_dir, "metadata.json")
            
            with open(meta_path, "w") as f:
                json.dump(out["model"], f, indent=2)

    # Publish proposed model messages to the message bus (aligned with standard UDMI subfolder schemas)
    if conn_spec and outputs:
        connection = ButlerConnection(conn_spec, registry_id, site_model)
        actual_registry = connection.actual_registry
        topic_msg_pairs = []
        for out in outputs:
            dev_id = out["deviceId"]
            tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
            source_id = "butler"
            model = out["model"]
            update_from = out.get("updateFrom", "")
            
            # Publish sharded subfolder proposals (unwrapped payload matching subFolder schema)
            subfolders_to_publish = []
            for sub_folder in ["localnet", "pointset", "system", "gateway"]:
                if sub_folder in model and isinstance(model[sub_folder], dict) and model[sub_folder]:
                    sub_payload = dict(model[sub_folder])
                    sub_payload["version"] = model.get("version", "1.5.7")
                    sub_payload["timestamp"] = model.get("timestamp", now)
                    subfolders_to_publish.append((sub_folder, sub_payload))

            if not subfolders_to_publish:
                subfolders_to_publish.append(("localnet", {"version": "1.5.7", "timestamp": now, "families": {}}))

            for sub_folder, sub_payload in subfolders_to_publish:
                msg = {
                    "subType": "propose",
                    "subFolder": sub_folder,
                    "deviceRegistryId": actual_registry,
                    "deviceId": dev_id,
                    "projectId": connection.project or "vibrant",
                    "transactionId": tx_id,
                    "publishTime": now,
                    "updateFrom": update_from,
                    "source": source_id,
                    "principal": source_id,
                    "payload": sub_payload,
                }
                topics = connection.get_propose_topics(dev_id, sub_folder)
                for t in topics:
                    topic_msg_pairs.append((t, msg))
        connection.publish_messages(topic_msg_pairs)
