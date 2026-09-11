"""Barbican Data Provider for UDMI MCP Server.

Provides datastore access and extracts UDMI device hierarchies,
registries, and properties with natural ordering.
"""

import base64
import functools
import json
import os
import re
import socket
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional, Set, Tuple


def natural_compare(s1: Optional[str], s2: Optional[str]) -> int:
    """Natural alphanumeric comparator mirroring Java EtcdExplorerServer.NATURAL_COMPARATOR."""
    if s1 is None and s2 is None:
        return 0
    if s1 is None:
        return -1
    if s2 is None:
        return 1
    c1 = re.findall(r"(\d+|\D+)", str(s1))
    c2 = re.findall(r"(\d+|\D+)", str(s2))
    for chunk1, chunk2 in zip(c1, c2):
        if chunk1.isdigit() and chunk2.isdigit():
            n1, n2 = int(chunk1), int(chunk2)
            if n1 != n2:
                return -1 if n1 < n2 else 1
            if len(chunk1) != len(chunk2):
                return -1 if len(chunk1) < len(chunk2) else 1
        else:
            lower1, lower2 = chunk1.lower(), chunk2.lower()
            if lower1 != lower2:
                return -1 if lower1 < lower2 else 1
            if chunk1 != chunk2:
                return -1 if chunk1 < chunk2 else 1
    if len(c1) != len(c2):
        return -1 if len(c1) < len(c2) else 1
    return 0


NATURAL_SORT_KEY = functools.cmp_to_key(natural_compare)
LAST_STATE_KEY_SUFFIX = ":last_state"


def prefix_range_end(prefix: str) -> bytes:
    """Computes the upper bound key for a prefix range scan."""
    b = bytearray(prefix.encode("utf-8"))
    for i in range(len(b) - 1, -1, -1):
        if b[i] < 0xFF:
            b[i] += 1
            return bytes(b[: i + 1])
    return b"\x00"


class BarbicanProvider:
    """Encapsulates Barbican datastore communication and UDMI model querying."""

    def __init__(
        self,
        target: Optional[str] = None,
        ca_file: Optional[str] = None,
        cert_file: Optional[str] = None,
        key_file: Optional[str] = None,
    ):
        if target is None:
            target = os.environ.get("ETCD_TARGET") or os.environ.get("ETCD_URL")
        if target is None and os.environ.get("ETCD_CLUSTER"):
            target = f"https://{os.environ['ETCD_CLUSTER']}:2379"
        if target is None:
            target = self.discover_target()
        elif isinstance(target, int):
            target = f"http://127.0.0.1:{target}"
        elif not target.startswith("http://") and not target.startswith("https://"):
            target = f"http://{target}"
        self.target = target.rstrip("/")

        # Resolve SSL credentials if https
        self.ca_file = ca_file or os.environ.get("ETCD_CA_FILE")
        self.cert_file = cert_file or os.environ.get("ETCD_CERT_FILE")
        self.key_file = key_file or os.environ.get("ETCD_KEY_FILE")

        ssl_dir = os.environ.get("SSL_SECRETS_DIR", "/etc/udmis/certs")
        if not self.ca_file:
            for c in [
                os.path.join(ssl_dir, "ca.crt"),
                "/etc/udmis/certs/ca.crt",
                "/etc/etcd/certs/client/ca.crt",
            ]:
                if os.path.exists(c):
                    self.ca_file = c
                    break

        if not self.cert_file:
            for c in [
                os.path.join(ssl_dir, "tls.crt"),
                os.path.join(ssl_dir, "rsa_private.crt"),
                "/etc/udmis/certs/tls.crt",
                "/etc/etcd/certs/client/tls.crt",
            ]:
                if os.path.exists(c):
                    self.cert_file = c
                    break

        if not self.key_file:
            for c in [
                os.path.join(ssl_dir, "tls.key"),
                os.path.join(ssl_dir, "rsa_private.key"),
                os.path.join(ssl_dir, "rsa_private.pem"),
                "/etc/udmis/certs/tls.key",
                "/etc/etcd/certs/client/tls.key",
            ]:
                if os.path.exists(c):
                    self.key_file = c
                    break

        self.ssl_context = None
        if self.target.startswith("https://"):
            import ssl
            if self.ca_file and os.path.exists(self.ca_file):
                self.ssl_context = ssl.create_default_context(cafile=self.ca_file)
            else:
                self.ssl_context = ssl.create_default_context()
            if self.cert_file and self.key_file and os.path.exists(self.cert_file) and os.path.exists(self.key_file):
                self.ssl_context.load_cert_chain(certfile=self.cert_file, keyfile=self.key_file)
            if os.environ.get("ETCD_INSECURE", "").lower() in ("true", "1", "yes"):
                self.ssl_context.check_hostname = False
                self.ssl_context.verify_mode = ssl.CERT_NONE

    @classmethod
    def discover_target(cls, candidates: Optional[List[int]] = None) -> str:
        """Find an active datastore port among candidates, or return the canonical default."""
        if candidates is not None:
            for port in candidates:
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                        return f"http://127.0.0.1:{port}"
                except Exception:
                    continue
            raise RuntimeError("No reachable Barbican/etcd datastore found")

        env_port = os.environ.get("ETCD_PORT")
        search_ports: List[int] = []
        if env_port:
            try:
                search_ports.append(int(env_port))
            except ValueError:
                pass
        for p in [18834, 2379]:
            if p not in search_ports:
                search_ports.append(p)

        for port in search_ports:
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                    return f"http://127.0.0.1:{port}"
            except Exception:
                continue

        default_port = env_port if env_port else 2379
        return f"http://127.0.0.1:{default_port}"

    def _request(
        self, endpoint: str, payload: Optional[Dict[str, Any]] = None, timeout: float = 5.0
    ) -> Dict[str, Any]:
        """Send an HTTP JSON request to the datastore gateway."""
        url = f"{self.target}{endpoint}"
        data = json.dumps(payload).encode("utf-8") if payload else None
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
        try:
            urlopen_kwargs = {"timeout": timeout}
            if self.ssl_context:
                urlopen_kwargs["context"] = self.ssl_context
            with urllib.request.urlopen(req, **urlopen_kwargs) as resp:
                resp_bytes = resp.read()
                if not resp_bytes:
                    return {}
                return json.loads(resp_bytes.decode("utf-8"))
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Datastore request to {endpoint} failed (HTTP {e.code}): {err_body}") from e
        except Exception as e:
            raise RuntimeError(f"Datastore connection to {url} failed: {e}") from e

    def health(self) -> Dict[str, Any]:
        """Check connection health of the Barbican datastore."""
        try:
            url = f"{self.target}/health"
            req = urllib.request.Request(url, method="GET")
            urlopen_kwargs = {"timeout": 1.0}
            if self.ssl_context:
                urlopen_kwargs["context"] = self.ssl_context
            with urllib.request.urlopen(req, **urlopen_kwargs) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                is_up = data.get("health") in ["true", True]
                return {
                    "status": "UP" if is_up else "DEGRADED",
                    "service": "barbican",
                    "connected": is_up,
                }
        except Exception:
            try:
                self.get_entry("__health_probe__")
                return {
                    "status": "UP",
                    "service": "barbican",
                    "connected": True,
                }
            except Exception:
                return {
                    "status": "DOWN",
                    "service": "barbican",
                    "connected": False,
                }

    def get_entry(self, key: str) -> Optional[str]:
        """Fetch the exact string value of a single key."""
        payload = {
            "key": base64.b64encode(key.encode("utf-8")).decode("ascii"),
        }
        res = self._request("/v3/kv/range", payload)
        kvs = res.get("kvs", [])
        if not kvs:
            return None
        val_b64 = kvs[0].get("value", "")
        return base64.b64decode(val_b64).decode("utf-8", errors="replace")

    def get_prefix_entries(self, prefix: str) -> Dict[str, str]:
        """Fetch all key-value pairs matching a prefix."""
        range_end = prefix_range_end(prefix)
        payload = {
            "key": base64.b64encode(prefix.encode("utf-8")).decode("ascii"),
            "range_end": base64.b64encode(range_end).decode("ascii"),
        }
        res = self._request("/v3/kv/range", payload)
        entries: Dict[str, str] = {}
        for item in res.get("kvs", []):
            k = base64.b64decode(item["key"]).decode("utf-8", errors="replace")
            v = base64.b64decode(item.get("value", "")).decode("utf-8", errors="replace")
            entries[k] = v
        return entries

    def get_prefix_keys(self, prefix: str) -> List[str]:
        """Fetch all keys matching a prefix, sorted by natural order."""
        range_end = prefix_range_end(prefix)
        payload = {
            "key": base64.b64encode(prefix.encode("utf-8")).decode("ascii"),
            "range_end": base64.b64encode(range_end).decode("ascii"),
            "keys_only": True,
        }
        res = self._request("/v3/kv/range", payload)
        keys = [
            base64.b64decode(item["key"]).decode("utf-8", errors="replace")
            for item in res.get("kvs", [])
        ]
        keys.sort(key=NATURAL_SORT_KEY)
        return keys

    def put_entry(self, key: str, value: str) -> bool:
        """Store a key-value entry in the datastore."""
        payload = {
            "key": base64.b64encode(key.encode("utf-8")).decode("ascii"),
            "value": base64.b64encode(value.encode("utf-8")).decode("ascii"),
        }
        res = self._request("/v3/kv/put", payload)
        return "header" in res

    def delete_entry(self, key: str, is_prefix: bool = False) -> int:
        """Delete a key or prefix from the datastore, returning the count of deleted keys."""
        payload: Dict[str, Any] = {
            "key": base64.b64encode(key.encode("utf-8")).decode("ascii"),
        }
        if is_prefix:
            payload["range_end"] = base64.b64encode(prefix_range_end(key)).decode("ascii")
        res = self._request("/v3/kv/deleterange", payload)
        return int(res.get("deleted", 0))

    def list_registries(self, prefix: str = "/r/") -> Dict[str, Any]:
        """List unique registries and count of total registered devices."""
        keys = self.get_prefix_keys(prefix)
        unique_registries: Set[str] = set()
        unique_devices: Set[str] = set()

        for key in keys:
            if key.startswith(prefix) and not key.endswith(LAST_STATE_KEY_SUFFIX):
                sub = key[len(prefix):]
                slash_idx = sub.find("/")
                colon_idx = sub.find(":")
                if slash_idx >= 0 and colon_idx >= 0:
                    end_idx = min(slash_idx, colon_idx)
                else:
                    end_idx = max(slash_idx, colon_idx)
                reg = sub[:end_idx] if end_idx >= 0 else sub
                if reg:
                    unique_registries.add(reg)

                device_idx = sub.find("/d/")
                if device_idx >= 0:
                    dev_sub = sub[device_idx + 3:]
                    dev_slash = dev_sub.find("/")
                    dev_colon = dev_sub.find(":")
                    if dev_slash >= 0 and dev_colon >= 0:
                        dev_end = min(dev_slash, dev_colon)
                    else:
                        dev_end = max(dev_slash, dev_colon)
                    dev = dev_sub[:dev_end] if dev_end >= 0 else dev_sub
                    if reg and dev:
                        unique_devices.add(f"{reg}/{dev}")

        sorted_regs = sorted(unique_registries, key=NATURAL_SORT_KEY)
        return {
            "registries": sorted_regs,
            "totalDevicesCount": len(unique_devices),
        }

    def list_devices(self, registry_id: str) -> Dict[str, Any]:
        """List all devices in a specified registry with natural ordering."""
        prefix = f"/r/{registry_id}/d/"
        keys = self.get_prefix_keys(prefix)
        unique_devices: Set[str] = set()

        for key in keys:
            if key.startswith(prefix) and not key.endswith(LAST_STATE_KEY_SUFFIX):
                sub = key[len(prefix):]
                slash_idx = sub.find("/")
                colon_idx = sub.find(":")
                if slash_idx >= 0 and colon_idx >= 0:
                    end_idx = min(slash_idx, colon_idx)
                else:
                    end_idx = max(slash_idx, colon_idx)
                dev = sub[:end_idx] if end_idx >= 0 else sub
                if dev:
                    unique_devices.add(dev)

        sorted_devices = sorted(unique_devices, key=NATURAL_SORT_KEY)
        return {
            "registryId": registry_id,
            "devices": sorted_devices,
        }

    def get_device_properties(self, registry_id: str, device_id: str) -> Dict[str, Any]:
        """Retrieve key-value properties for a device in a registry."""
        prefix = f"/r/{registry_id}/d/{device_id}"
        entries: Dict[str, str] = {}
        entries.update(self.get_prefix_entries(f"{prefix}:"))
        entries.update(self.get_prefix_entries(f"{prefix}/"))
        exact_val = self.get_entry(prefix)
        if exact_val is not None:
            entries[prefix] = exact_val

        raw_props: Dict[str, str] = {}
        for k, v in entries.items():
            if k.startswith(prefix):
                prop_key = k[len(prefix):]
                if not prop_key:
                    prop_key = ":value"
                raw_props[prop_key] = v

        sorted_props = {
            k: raw_props[k] for k in sorted(raw_props.keys(), key=NATURAL_SORT_KEY)
        }
        return {
            "registryId": registry_id,
            "deviceId": device_id,
            "properties": sorted_props,
        }
