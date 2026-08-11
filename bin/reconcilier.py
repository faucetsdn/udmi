#!/usr/bin/env python3
import sys
import psycopg2
import json
import os

POSTGRES_PORT = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_USER = os.environ.get("POSTGRES_USER", "postgres")
POSTGRES_DB = os.environ.get("POSTGRES_DB", "postgres")
POSTGRES_HOST = "127.0.0.1"

def get_postgres_connection():
    return psycopg2.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        user=POSTGRES_USER,
        dbname=POSTGRES_DB
    )

def main():
    if len(sys.argv) < 2:
        print("Usage: bin/reconcilier <registryId>")
        sys.exit(1)
        
    registry_id = sys.argv[1]
    
    try:
        conn = get_postgres_connection()
    except psycopg2.Error as e:
        print(f"Error connecting to DB: {e}")
        sys.exit(1)
        
    cursor = conn.cursor()
    
    # 1. Get most recent model for each device_id in this registry
    cursor.execute("""
        SELECT device_id, payload
        FROM (
            SELECT device_id, payload,
                   ROW_NUMBER() OVER(PARTITION BY device_id ORDER BY created_at DESC) as rn
            FROM udmi_messages
            WHERE registry_id = %s AND sub_type = 'model'
        ) sub
        WHERE rn = 1
    """, (registry_id,))
    
    models = cursor.fetchall()
    
    # 2. Get all discovery events for this registry to extract discovered bacnet IDs
    cursor.execute("""
        SELECT payload
        FROM udmi_messages
        WHERE registry_id = %s AND sub_folder = 'discovery' AND sub_type = 'events'
    """, (registry_id,))
    
    discovery_events = cursor.fetchall()
    
    discovered_devices = []
    
    for row in discovery_events:
        payload = row[0]
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except:
                continue
        
        # Check root level primary family
        bacnet_addr = None
        ipv4_addr = None
        
        if payload.get('family') == 'bacnet':
            bacnet_addr = payload.get('addr')

        # Check secondary families block
        families = payload.get('families', {})
        if 'bacnet' in families:
            bacnet_addr = bacnet_addr or families['bacnet'].get('addr')
        if 'ipv4' in families:
            ipv4_addr = families['ipv4'].get('addr')
            
        if bacnet_addr or ipv4_addr:
            discovered_devices.append({
                'bacnet': str(bacnet_addr) if bacnet_addr else None,
                'ipv4': str(ipv4_addr) if ipv4_addr else None
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
            
        modeled_devices.append({
            'device_id': device_id,
            'bacnet': str(bacnet_addr) if bacnet_addr else None,
            'ipv4': str(ipv4_addr) if ipv4_addr else None
        })
            
    # Combine everything using a union-find approach to group related addresses
    entities = []
    
    # 1. Initialize entities from models
    for m in modeled_devices:
        ent = { 'device_id': m['device_id'], 'bacnet': set(), 'ipv4': set() }
        if m['bacnet']: ent['bacnet'].add(m['bacnet'])
        if m['ipv4']: ent['ipv4'].add(m['ipv4'])
        entities.append(ent)
        
    # 2. Process discovery events and merge
    for d in discovered_devices:
        d_bacnet = d['bacnet']
        d_ipv4 = d['ipv4']
        
        matching_indices = []
        for idx, ent in enumerate(entities):
            if (d_bacnet and d_bacnet in ent['bacnet']) or \
               (d_ipv4 and d_ipv4 in ent['ipv4']):
                matching_indices.append(idx)
                
        if not matching_indices:
            # Create a new entity for this unmatched discovery event
            new_ent = { 'device_id': None, 'bacnet': set(), 'ipv4': set() }
            if d_bacnet: new_ent['bacnet'].add(d_bacnet)
            if d_ipv4: new_ent['ipv4'].add(d_ipv4)
            entities.append(new_ent)
        else:
            # Merge the discovery addresses into the first matching entity
            primary = entities[matching_indices[0]]
            if d_bacnet: primary['bacnet'].add(d_bacnet)
            if d_ipv4: primary['ipv4'].add(d_ipv4)
            
            # If the discovery event bridged multiple existing entities, merge them together
            for other_idx in reversed(matching_indices[1:]):
                other = entities.pop(other_idx)
                primary['bacnet'].update(other['bacnet'])
                primary['ipv4'].update(other['ipv4'])
                
                # Combine device IDs if needed
                if not primary['device_id']:
                    primary['device_id'] = other['device_id']
                elif other['device_id'] and primary['device_id'] != other['device_id']:
                    primary['device_id'] = f"{primary['device_id']}+{other['device_id']}"
                    
    # 3. Generate Model Diff Messages
    outputs = []
    unknown_counter = 1
    
    for ent in entities:
        dev_id = ent['device_id']
        
        # Pick a single address if multiple exist
        b_addr = sorted(ent['bacnet'])[0] if ent['bacnet'] else None
        i_addr = sorted(ent['ipv4'])[0] if ent['ipv4'] else None
        
        if dev_id:
            # Existing device: calculate diff
            orig = original_models.get(dev_id, {})
            orig_localnet = orig.get('localnet', {}).get('families', {})
            
            orig_b = orig_localnet.get('bacnet', {}).get('addr') if 'bacnet' in orig_localnet else None
            orig_i = orig_localnet.get('ipv4', {}).get('addr') if 'ipv4' in orig_localnet else None
            
            diff_families = {}
            if b_addr and b_addr != orig_b:
                diff_families['bacnet'] = {'addr': b_addr}
            if i_addr and i_addr != orig_i:
                diff_families['ipv4'] = {'addr': i_addr}
                
            if diff_families:
                diff = {'localnet': {'families': diff_families}}
                outputs.append({'deviceId': dev_id, 'model': diff})
        else:
            # New device: create minimal model
            dev_id = f"unknown-{unknown_counter}"
            unknown_counter += 1
            
            new_families = {}
            if b_addr: new_families['bacnet'] = {'addr': b_addr}
            if i_addr: new_families['ipv4'] = {'addr': i_addr}
            
            diff = {
                'version': '1.5.2',
                'localnet': {'families': new_families}
            }
            outputs.append({'deviceId': dev_id, 'model': diff})
            
    conn_spec = sys.argv[2] if len(sys.argv) > 2 else None
    
    if conn_spec:
        from urllib.parse import urlparse
        import subprocess
        
        spec = conn_spec
        provider_ssl = False

        if not spec.startswith("//"):
            parsed = urlparse(spec)
            if parsed.scheme in ("mqtt", "mqtts", "ssl"):
                host_port = parsed.netloc.split("@")[-1]
                prefix = parsed.path.strip("/")
                if not prefix and parsed.username and not parsed.password:
                    prefix = parsed.username
                spec = f"//mqtt/{host_port}/{prefix}" if prefix else f"//mqtt/{host_port}"

        if spec.startswith("//"):
            spec_body = spec[2:]
            if "/" in spec_body:
                provider, endpoint = spec_body.split("/", 1)
                provider_ssl = provider in ("ssl", "mqtts", "tcps", "wss")
                spec = f"mqtt://{endpoint}"

        url = urlparse(spec)
        host = url.hostname or "localhost"
        is_ssl = provider_ssl or url.scheme in ("ssl", "mqtts", "tcps", "wss")
        port = url.port or (8883 if is_ssl else 1883)

        prefix = url.path.strip("/") if url.path and url.path != "/" else ""

        for out in outputs:
            dev_id = out["deviceId"]
            topic = f"/uufi/r/{registry_id}/d/{dev_id}/c/model"
            if prefix:
                topic = f"/{prefix}{topic}"
                
            payload_str = json.dumps(out["model"])
            
            cmd = [
                "mosquitto_pub",
                "-h", host,
                "-p", str(port),
                "-t", topic,
                "-m", payload_str
            ]
            
            if registry_id:
                cmd.extend(["-i", f"/uufi/{registry_id}/client"])
                
            if url.username:
                cmd.extend(["-u", url.username])
                if url.password:
                    cmd.extend(["-P", url.password])
                    
            if is_ssl:
                cmd.extend(["--insecure"])
                
            subprocess.run(cmd, check=True)
    else:
        for out in outputs:
            print(json.dumps(out))

if __name__ == "__main__":
    main()
