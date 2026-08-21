"""Project specification parsing utilities for UDMI."""

import os
from typing import Any, Dict, Optional


def parse_project_spec(spec: Optional[str]) -> Dict[str, Any]:
    """Parses project spec conforming to: [//provider/]project[/namespace][+user].

    Args:
        spec: Project specification string.

    Returns:
        Dictionary containing parsed components:
            provider: str ('mqtt', 'pubsub', 'gbos', 'clearblade', 'jwt', etc.)
            project: str (hostname or GCP project id)
            port: Optional[int] (port override if provided)
            namespace: Optional[str] (registry namespace prefix)
            prefix: str (namespace string)
            user: Optional[str] (username suffix)
    """
    if not spec:
        return {
            "provider": "mqtt",
            "project": "localhost",
            "namespace": None,
            "port": None,
            "prefix": "",
            "user": None,
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

    namespace = None
    prefix = ""
    port = None

    if "/" in s:
        project, rest = s.split("/", 1)
        namespace = rest
        prefix = rest
    else:
        project = s

    if ":" in project:
        project, port_str = project.split(":", 1)
        try:
            port = int(port_str)
        except ValueError:
            pass

    if provider in ("ssl", "mqtts"):
        provider = "mqtt"

    # Environment port overrides if not explicitly specified in spec
    if port is None and provider == "mqtt":
        env_mqtt_port = os.environ.get("MQTT_PORT")
        if env_mqtt_port:
            try:
                port = int(env_mqtt_port)
            except ValueError:
                pass

    return {
        "provider": (provider or "mqtt").lower(),
        "project": project or "localhost",
        "port": port,
        "namespace": namespace,
        "prefix": prefix,
        "user": user,
    }
