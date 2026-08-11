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
    
    for device_id, payload in models:
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except:
                continue
                
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
                    
    # 3. Format output
    outputs = []
    unknown_counter = 1
    
    for ent in entities:
        dev_id = ent['device_id']
        if not dev_id:
            dev_id = f"unknown-{unknown_counter}"
            unknown_counter += 1
            
        bacnet_str = ','.join(sorted(ent['bacnet'])) if ent['bacnet'] else ""
        ipv4_str = ','.join(sorted(ent['ipv4'])) if ent['ipv4'] else ""
        
        outputs.append((dev_id, bacnet_str, ipv4_str))
            
    outputs.sort(key=lambda x: x[0])
    
    outputs.sort(key=lambda x: x[0])
    
    if outputs:
        col1_len = max(max(len(x[0]) for x in outputs), len("deviceId"))
        col2_len = max(max(len(x[1]) for x in outputs), len("bacnet"))
        col3_len = max(max(len(x[2]) for x in outputs), len("ipv4"))
    else:
        col1_len, col2_len, col3_len = len("deviceId"), len("bacnet"), len("ipv4")
        
    print(f"| {'deviceId':<{col1_len}} | {'bacnet':<{col2_len}} | {'ipv4':<{col3_len}} |")
    print(f"| {'-' * col1_len} | {'-' * col2_len} | {'-' * col3_len} |")
    
    for out in outputs:
        print(f"| {out[0]:<{col1_len}} | {out[1]:<{col2_len}} | {out[2]:<{col3_len}} |")

if __name__ == "__main__":
    main()
