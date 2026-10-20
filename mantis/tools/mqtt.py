"""MQTT telemetry injection and publication tool for Mantis."""

from typing import Any, Dict, Optional

from mantis.session import SessionManager
from mantis.project_spec import is_cloud_spec, parse_project_spec, resolve_target_spec


def publish_mqtt_message(
    session_mgr: SessionManager,
    test_id: str,
    topic: str,
    payload: str,
    target_spec: Optional[str] = None,
    site_model: str = "sites/udmi_site_model",
    device_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Publish a message to Mosquitto MQTT broker or remote cloud endpoint (gbos, gref, pubsub, clearblade)."""
    info = session_mgr.get_session_info(test_id) or {}
    resolved_spec = resolve_target_spec(
        target_spec=target_spec,
        session_info=info,
        site_model=site_model,
    )
    is_cloud = is_cloud_spec(resolved_spec)

    # 1. Cloud Target Execution (TLS, JWT, or PubSub)
    if is_cloud:
        spec_info = parse_project_spec(resolved_spec)
        provider = spec_info.get("provider", "mqtt")

        if provider == "pubsub":
            try:
                from google.cloud import pubsub_v1  # type: ignore
                project = spec_info.get("project")
                pub_topic = topic
                if not pub_topic.startswith("projects/"):
                    pub_topic = f"projects/{project}/topics/{topic}"
                publisher = pubsub_v1.PublisherClient()
                data = payload.encode("utf-8") if isinstance(payload, str) else payload
                future = publisher.publish(pub_topic, data)
                msg_id = future.result(timeout=10)
                return {
                    "status": "PUBLISHED",
                    "test_id": test_id,
                    "topic": pub_topic,
                    "payload": payload,
                    "target_spec": resolved_spec,
                    "is_cloud": True,
                    "provider": "pubsub",
                    "message_id": msg_id,
                }
            except Exception as e:
                return {
                    "status": "ERROR",
                    "test_id": test_id,
                    "topic": topic,
                    "target_spec": resolved_spec,
                    "error": f"PubSub publish failed: {e}",
                }

        # gbos, clearblade, jwt, or remote mqtt broker with TLS
        try:
            from udmi.common.connection import MessageConnection
            conn = MessageConnection(
                conn_spec=resolved_spec,
                site_model=site_model,
                device_id=device_id or "UDMI-REFLECT",
            )
            conn.publish_messages([(topic, payload)])
            return {
                "status": "PUBLISHED",
                "test_id": test_id,
                "topic": topic,
                "payload": payload,
                "target_spec": resolved_spec,
                "is_cloud": True,
                "provider": provider,
            }
        except Exception as e:
            return {
                "status": "ERROR",
                "test_id": test_id,
                "topic": topic,
                "target_spec": resolved_spec,
                "error": str(e),
            }

    # 2. Local Broker Execution (Mosquitto)
    ports = info.get("ports", {})
    mqtt_port = ports.get("mqtt")

    spec_info = parse_project_spec(resolved_spec)
    host = spec_info.get("project") or "127.0.0.1"
    if spec_info.get("port"):
        mqtt_port = spec_info["port"]

    if not mqtt_port:
        return {
            "status": "ERROR",
            "test_id": test_id,
            "topic": topic,
            "target_spec": resolved_spec,
            "error": f"No MQTT port specified in session for '{test_id}' or target_spec '{resolved_spec}'",
        }

    creds = info.get("credentials")

    try:
        import paho.mqtt.publish as publish
        publish.single(
            topic=topic,
            payload=payload,
            hostname=host,
            port=mqtt_port,
            auth=creds,
        )
        return {
            "status": "PUBLISHED",
            "test_id": test_id,
            "topic": topic,
            "payload": payload,
            "target_spec": resolved_spec,
            "is_cloud": False,
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "test_id": test_id,
            "topic": topic,
            "target_spec": resolved_spec,
            "error": str(e),
        }
