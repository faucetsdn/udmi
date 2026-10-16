"""UDMI project specification parsing and resolution utilities for Mantis."""

import hashlib
import json
import os
from typing import Any, Dict, Optional

try:
    from udmi.common.project_spec import parse_project_spec as base_parse_project_spec
except ImportError:
    base_parse_project_spec = None


def derive_port_from_namespace(namespace: str) -> int:
    """Derive deterministic port number from namespace string (20000..54990)."""
    slot = int(hashlib.sha256(namespace.encode("utf-8")).hexdigest(), 16) % 3500
    return 20000 + slot * 10


def parse_project_spec(spec: Optional[str]) -> Dict[str, Any]:
    """Parses project spec conforming to: [//provider/]project[@bridge_host][%registry][/namespace][+user].

    Returns:
        Dictionary containing parsed components:
            provider: str ('mqtt', 'pubsub', 'gbos', 'gref', 'clearblade', 'jwt', etc.)
            project: Optional[str] (hostname or GCP project id, None for NO_SITE)
            bridge_host: Optional[str] (explicit bridge host or hostname:port)
            port: Optional[int] (port override if provided)
            namespace: Optional[str] (registry namespace prefix)
            prefix: str (namespace string)
            user: Optional[str] (username suffix)
            registry: Optional[str] (registry id override from %registry)
            is_cloud: bool (whether target connects to a remote cloud service)
    """
    if not spec:
        return {
            "provider": "mqtt",
            "project": "localhost",
            "bridge_host": None,
            "namespace": None,
            "port": None,
            "prefix": "",
            "user": None,
            "registry": None,
            "is_cloud": False,
        }

    s = spec.strip()
    provider = None

    if s.startswith("//"):
        s = s[2:]
        if "/" in s:
            provider, s = s.split("/", 1)
        else:
            provider = s
            s = ""
    elif "://" in s:
        proto, s = s.split("://", 1)
        provider = "mqtt" if proto in ("mqtt", "mqtts", "ssl") else proto

    user = None
    if "+" in s:
        s, user = s.split("+", 1)
    elif " " in s:
        s, user = s.split(" ", 1)

    registry = None
    if "%" in s:
        s, registry = s.split("%", 1)

    namespace = None
    if "/" in s:
        project_part, namespace = s.split("/", 1)
    else:
        project_part = s

    bridge_host = None
    project = project_part
    port = None

    # Handle @ syntax:
    # 1. URL basic auth user:pass@host (has colon before @)
    # 2. UDMI project_id@bridge_host (no colon before @, or project is _)
    if "@" in project_part:
        prefix_part, suffix_part = project_part.split("@", 1)
        if ":" in prefix_part:
            # URL basic auth: strip user:pass
            project = suffix_part
        else:
            # UDMI project_id@bridge_host
            project = None if prefix_part in ("_", "--") else prefix_part
            bridge_host = suffix_part

    if project and ":" in project:
        project, port_str = project.split(":", 1)
        try:
            port = int(port_str)
        except ValueError:
            pass
        if not bridge_host:
            bridge_host = f"{project}:{port}" if port else f"{project}:{port_str}"

    if bridge_host and ":" in bridge_host and port is None:
        try:
            port = int(bridge_host.split(":", 1)[1])
        except ValueError:
            pass

    if provider in ("ssl", "mqtts"):
        provider = "mqtt"

    # Environment port overrides if not explicitly specified in spec
    if port is None and (provider == "mqtt" or provider is None):
        env_mqtt_port = os.environ.get("MQTT_PORT")
        if env_mqtt_port:
            try:
                port = int(env_mqtt_port)
            except ValueError:
                pass

    # Default provider resolution
    if not provider:
        if project and ("bos-platform" in project or project.startswith("gcp-")):
            provider = "gbos"
        else:
            provider = "mqtt"
    prov = provider.lower()
    proj = project

    # Determine is_cloud
    cloud_providers = ("gbos", "gref", "pubsub", "clearblade", "iotcore", "jwt", "gcp")
    local_hosts = ("localhost", "127.0.0.1", None, "_", "--")
    if prov in cloud_providers:
        is_cloud = True
    elif bridge_host and not any(bridge_host.startswith(h) for h in ("localhost", "127.0.0.1")):
        is_cloud = True
    elif proj and ("bos-platform" in proj or "gcp" in proj):
        is_cloud = True
    elif prov == "mqtt" and proj not in local_hosts:
        is_cloud = True
    else:
        is_cloud = False


    return {
        "provider": prov,
        "project": proj if proj is not None else "localhost",
        "bridge_host": bridge_host,
        "port": port,
        "namespace": namespace,
        "prefix": namespace or "",
        "user": user,
        "registry": registry,
        "is_cloud": is_cloud,
    }


def format_project_spec(parsed: Dict[str, Any]) -> str:
    """Format parsed project spec components back into canonical URI string."""
    provider = parsed.get("provider", "mqtt")
    project = parsed.get("project") or "localhost"
    bridge_host = parsed.get("bridge_host")
    port = parsed.get("port")
    namespace = parsed.get("namespace")
    user = parsed.get("user")
    registry = parsed.get("registry")

    if bridge_host and bridge_host != f"{project}:{port}" and bridge_host != project:
        endpoint = f"{project}@{bridge_host}"
    elif port:
        endpoint = f"{project}:{port}"
    else:
        endpoint = project

    if registry:
        endpoint = f"{endpoint}%{registry}"

    res = f"//{provider}/{endpoint}"
    if namespace:
        res += f"/{namespace}"
    if user:
        res += f"+{user}"
    return res


def normalize_project_spec(spec: Optional[str]) -> str:
    """Normalize arbitrary project spec string into canonical [//provider/]project form."""
    if not spec:
        return "//mqtt/localhost"

    s = spec.strip()
    if s in ("--", "mock-project", "mock-clean"):
        return s

    if s.startswith("//"):
        return s

    if s.startswith(("mqtt://", "mqtts://", "ssl://")):
        parsed = parse_project_spec(s)
        return format_project_spec(parsed)

    cloud_providers = ("gbos", "gref", "pubsub", "clearblade", "iotcore", "jwt", "gcp", "mqtt")
    if "/" in s:
        prefix = s.split("/", 1)[0]
        if prefix in cloud_providers:
            return f"//{s}"
        raise ValueError(
            f"Unrecognized provider '{prefix}' in target spec '{s}'. "
            f"Must start with '//' or a known provider ({', '.join(sorted(cloud_providers))})."
        )

    if s.startswith("bos-platform-"):
        return f"//gbos/{s}"

    if "@" in s:
        # e.g. bos-platform-dev@mqtt.bos.goog
        return f"//gbos/{s}"

    if ":" in s:
        # e.g. localhost:18833
        return f"//mqtt/{s}"

    if s in ("localhost", "127.0.0.1"):
        return f"//mqtt/{s}"

    # Semantic namespace (e.g. btesting, default)
    if s in ("btesting", "default") or s.startswith("test_"):
        port = derive_port_from_namespace(s)
        return f"//mqtt/localhost:{port}/{s}"

    raise ValueError(
        f"Unrecognized target project spec '{spec}'. "
        f"Specify a full URI (e.g. '//gbos/{s}' or '//mqtt/localhost:18833/{s}')."
    )


def is_cloud_spec(spec: Optional[str]) -> bool:
    """Determine whether project specification targets a cloud service."""
    if not spec or spec in ("--", "mock-project", "mock-clean"):
        return False
    parsed = parse_project_spec(spec)
    return parsed.get("is_cloud", False)


def resolve_target_spec(
    target_spec: Optional[str] = None,
    session_info: Optional[Dict[str, Any]] = None,
    site_model: Optional[str] = None,
) -> str:
    """Resolve target project specification following precedence hierarchy:

    1. Explicit target_spec parameter
    2. TARGET_PROJECT environment variable
    3. PROJECT_SPEC environment variable
    4. PROJECT_ID (+ IOT_PROVIDER, UDMI_NAMESPACE) environment variables
    5. Active session info
    6. Site model cloud_iot_config.json
    7. Default fallback (//mqtt/localhost)
    """
    if target_spec:
        return normalize_project_spec(target_spec)

    target_env = os.environ.get("TARGET_PROJECT")
    if target_env:
        return normalize_project_spec(target_env)

    spec_env = os.environ.get("PROJECT_SPEC")
    if spec_env:
        return normalize_project_spec(spec_env)

    proj_id = os.environ.get("PROJECT_ID")
    if proj_id:
        provider = os.environ.get("IOT_PROVIDER", "pubsub")
        namespace = os.environ.get("UDMI_NAMESPACE")
        spec = f"//{provider}/{proj_id}"
        if namespace:
            spec += f"/{namespace}"
        return normalize_project_spec(spec)

    if session_info and session_info.get("project_spec"):
        return normalize_project_spec(session_info["project_spec"])

    if site_model:
        cfg_file = os.path.join(site_model, "cloud_iot_config.json")
        if os.path.isfile(cfg_file):
            try:
                with open(cfg_file, "r", encoding="utf-8") as f:
                    cfg = json.load(f)
                provider = cfg.get("iot_provider")
                project = cfg.get("project_id")
                namespace = cfg.get("udmi_namespace")
                if provider and project:
                    spec = f"//{provider}/{project}"
                    if namespace:
                        spec += f"/{namespace}"
                    return normalize_project_spec(spec)
            except Exception:
                pass

    return "//mqtt/localhost"
