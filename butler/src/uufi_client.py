import os
import ssl
import paho.mqtt.client as mqtt
from urllib.parse import urlparse
import json
import sys
import uuid
import time
from datetime import datetime, timezone

class UufiClient:
    def __init__(self, conn_spec, registry_id=None, site_model=None):
        self.conn_spec = conn_spec
        self.registry_id = registry_id or "default"
        self.site_model = site_model
        self.prefix = ""
        self.host = "localhost"
        self.port = 1883
        self.username = None
        self.password = None
        self.is_ssl = False
        self._parse_conn_spec(conn_spec)
        
        self.client_id = f"butler_{uuid.uuid4().hex[:8]}"
        self.source_id = f"{self.registry_id}/{self.client_id}" if registry_id else self.client_id
        
        mqtt_client_id = f"/{self.prefix}/{self.registry_id}/{self.client_id}" if self.prefix else f"/{self.registry_id}/{self.client_id}"
        self.client = mqtt.Client(client_id=mqtt_client_id)
        
        if self.username:
            self.client.username_pw_set(self.username, self.password)
            
        if self.is_ssl:
            if getattr(self, "site_model", None):
                ca_cert = f"{self.site_model}/reflector/ca.crt"
                cert_file = f"{self.site_model}/reflector/rsa_private.crt"
                key_file = f"{self.site_model}/reflector/rsa_private.pem"
                try:
                    import os
                    if os.path.exists(ca_cert):
                        self.client.tls_set(ca_certs=ca_cert, certfile=cert_file, keyfile=key_file, cert_reqs=ssl.CERT_NONE)
                    else:
                        self.client.tls_set(cert_reqs=ssl.CERT_NONE)
                except Exception as e:
                    print(f"Error loading certs: {e}")
                    self.client.tls_set(cert_reqs=ssl.CERT_NONE)
            else:
                self.client.tls_set(cert_reqs=ssl.CERT_NONE)
            self.client.tls_insecure_set(True)
            
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        
        self.connected = False
        self.handshake_done = False
        self.handshake_tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
        
    def _parse_conn_spec(self, spec):
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
                self.is_ssl = provider in ("ssl", "mqtts", "tcps", "wss")
                spec = f"mqtt://{endpoint}"

        url = urlparse(spec)
        self.host = url.hostname or "localhost"
        self.is_ssl = self.is_ssl or url.scheme in ("ssl", "mqtts", "tcps", "wss") or str(url.port) == "46432" or str(url.port) == "8883"
        self.port = url.port or (8883 if self.is_ssl else 1883)
        self.prefix = url.path.strip("/") if url.path and url.path != "/" else ""
        self.username = url.username or os.environ.get("MQTT_USER", "rocket")
        self.password = url.password or os.environ.get("MQTT_PASS", "monkey")

    def _on_connect(self, client, userdata, flags, rc):
        self.connected = True
        topic = f"/{self.prefix}/uufi/c/config/udmi" if self.prefix else "/uufi/c/config/udmi"
        self.client.subscribe(topic)
        self._send_handshake()

    def _send_handshake(self):
        topic = f"/{self.prefix}/uufi/c/state/udmi" if self.prefix else "/uufi/c/state/udmi"
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        
        msg = {
            "subType": "state",
            "subFolder": "udmi",
            "projectId": "vibrant",
            "transactionId": self.handshake_tx_id,
            "publishTime": now,
            "source": self.source_id,
            "principal": self.source_id,
            "payload": {
                "version": "1.5.2",
                "timestamp": now,
                "setup": {
                    "functions_ver": 9,
                    "transaction_id": self.handshake_tx_id,
                    "msg_source": self.source_id
                }
            }
        }
        print(f"Publishing to {topic}: {msg}", file=sys.stderr)
        self.client.publish(topic, json.dumps(msg), qos=1)

    def _on_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode())
            if payload.get("subType") == "config" and payload.get("subFolder") == "udmi":
                if payload.get("transactionId") == self.handshake_tx_id:
                    self.handshake_done = True
        except Exception as e:
            print(f"Error parsing message: {e}")

    def connect(self):
        self.client.connect(self.host, self.port, 60)
        self.client.loop_start()
        
        timeout = 60
        start_time = time.time()
        while not self.handshake_done and time.time() - start_time < timeout:
            time.sleep(0.1)
            
        if not self.handshake_done:
            raise Exception("UUFI Handshake timeout")

    def disconnect(self):
        self.client.loop_stop()
        self.client.disconnect()

    def publish_model(self, device_id, model_payload):
        topic = f"/uufi/r/{self.registry_id}/d/{device_id}/c/model/system"
        if self.prefix:
            topic = f"/{self.prefix}{topic}"
            
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
        
        msg = {
            "subType": "model",
            "subFolder": "system",
            "deviceRegistryId": self.registry_id,
            "deviceId": device_id,
            "projectId": "vibrant",
            "transactionId": tx_id,
            "publishTime": now,
            "source": self.source_id,
            "principal": self.source_id,
            "payload": model_payload
        }
        print(f"Publishing to {topic}: {msg}", file=sys.stderr)
        self.client.publish(topic, json.dumps(msg), qos=1)

    def publish_discovery_config(self, device_id, generation, families):
        topic = f"/uufi/r/{self.registry_id}/d/{device_id}/c/config/discovery"
        if self.prefix:
            topic = f"/{self.prefix}{topic}"
            
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
        
        payload = {
            "version": "1.5.2",
            "timestamp": now,
            "generation": generation,
            "families": families,
            "enumerations": {"families": "entries", "points": "entries", "features": "entries"}
        }
        
        msg = {
            "subType": "config",
            "subFolder": "discovery",
            "deviceRegistryId": self.registry_id,
            "deviceId": device_id,
            "projectId": "vibrant",
            "transactionId": tx_id,
            "publishTime": now,
            "source": self.source_id,
            "principal": self.source_id,
            "payload": payload
        }
        print(f"Publishing to {topic}: {msg}", file=sys.stderr)
        self.client.publish(topic, json.dumps(msg), qos=1)
