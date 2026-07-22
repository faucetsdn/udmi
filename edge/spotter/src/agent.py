import argparse
import base64
import hashlib
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from typing import Any, Dict

from udmi.core.factory import create_device, ClientConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider, Basic, EndpointConfiguration, Protocol
from udmi.schema._base import DataModel

@dataclass
class PcapChunkEvent(DataModel):
    session_id: str
    chunk_index: int
    total_chunks: int
    data: str


LOGGER = logging.getLogger("spotter_agent")

def calculate_local_password(key_file: str) -> str:
    """Calculates password for udmi_local authentication mechanism.
    
    This is based on the first 8 characters of the sha256 hash of the pkcs8 private key.
    """
    pkcs_file = f"{key_file.rpartition('.')[0]}.pkcs8"
    if not os.path.exists(pkcs_file):
        # Fallback to key_file if pkcs8 file does not exist
        pkcs_file = key_file
    with open(pkcs_file, 'rb') as f:
        key_bytes = f.read()
        h = hashlib.sha256(key_bytes).hexdigest()
        return h[:8]

def build_endpoint_config(config: Dict[str, Any]) -> EndpointConfiguration:
    mqtt_config = config.get("mqtt", {})
    device_id = mqtt_config.get("device_id")
    spotter_device_id = mqtt_config.get("spotter_device_id", device_id)
    registry_id = mqtt_config.get("registry_id")
    host = mqtt_config.get("host", "localhost")
    port = int(mqtt_config.get("port", 8883))
    algorithm = mqtt_config.get("algorithm", "RS256")
    auth_mechanism = mqtt_config.get("authentication_mechanism", "jwt_gcp")
    key_file = mqtt_config.get("key_file")
    cert_file = mqtt_config.get("cert_file")
    ca_file = mqtt_config.get("ca_file")

    # In local testing or mTLS on-prem, the topic prefix might look like /r/registry/d/device
    # But clientlib expects the prefix without the device ID if we use it with create_device
    # because create_device wires it up.
    if auth_mechanism == "jwt_gcp":
        topic_prefix = f"/devices/"
        client_id = f"projects/{mqtt_config.get('project_id')}/locations/{mqtt_config.get('region')}/registries/{registry_id}/devices/{spotter_device_id}"
    else:
        topic_prefix = f"/r/{registry_id}/d/"
        client_id = f"/r/{registry_id}/d/{spotter_device_id}"
        client_id = mqtt_config.get("spotter_client_id", client_id)

    auth_provider = None
    if auth_mechanism == "udmi_local":
        username = f"/r/{registry_id}/d/{spotter_device_id}"
        password = calculate_local_password(key_file)
        auth_provider = AuthProvider(
            basic=Basic(
                username=username,
                password=password
            )
        )
    elif auth_mechanism in ("jwt_gcp", "jwt"):
        from udmi.schema import Jwt
        auth_provider = AuthProvider(
            jwt=Jwt(
                audience=mqtt_config.get("project_id")
            )
        )

    return EndpointConfiguration(
        client_id=client_id,
        hostname=host,
        port=port,
        topic_prefix=topic_prefix,
        algorithm=algorithm,
        auth_provider=auth_provider,
        ca_file=ca_file,
        cert_file=cert_file,
        key_file=key_file,
        protocol=Protocol.mqtt
    )

def main():
    parser = argparse.ArgumentParser(description="Start Spotter Core Agent")
    parser.add_argument(
        "--config_file",
        type=str,
        help="path to config file",
        required=True
    )
    args = parser.parse_args()

    # Read config
    with open(args.config_file, "r") as f:
        config = json.load(f)

    # Setup logging
    log_level_str = str(config.get("log_level", "INFO")).upper()
    log_level = getattr(logging, log_level_str, logging.INFO)
    
    # Ensure logs directory exists if logging to file
    log_dir = "/var/log/spotter"
    if os.path.exists(log_dir) and os.access(log_dir, os.W_OK):
        log_file = os.path.join(log_dir, "agent.log")
        handler = logging.FileHandler(log_file)
    else:
        handler = logging.StreamHandler(sys.stdout)
        
    logging.basicConfig(
        format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
        handlers=[handler],
        level=log_level,
    )
    LOGGER.setLevel(log_level)

    LOGGER.info("Starting Spotter Core Agent...")
    endpoint_config = build_endpoint_config(config)
    LOGGER.info("Endpoint Config: %s", endpoint_config)

    # Initialize device using clientlib
    # Note: we need key_file for JWT or mTLS.
    key_file = config.get("mqtt", {}).get("key_file")
    
    # We populate the client config so that the messaging library enables TLS
    ca_file = config.get("mqtt", {}).get("ca_file")
    cert_file = config.get("mqtt", {}).get("cert_file")
    insecure_tls = config.get("mqtt", {}).get("authentication_mechanism") == "udmi_local"
    tls_config = TlsConfig(
        ca_certs=ca_file,
        cert_file=cert_file,
        key_file=key_file,
        insecure=insecure_tls
    )
    client_config = ClientConfig(tls_config=tls_config)
    
    # We create the device. Register only SystemManager to avoid conflicts with legacy discovery.
    device = create_device(
        endpoint_config,
        managers=[SystemManager()],
        client_config=client_config,
        key_file=key_file
    )

    # Retrieve SystemManager and register our custom pcap_capture blob handler
    from pcap import capture_packets
    from uploader import ResumableUploader

    def process_pcap_blob(blob_key: str, file_path: str) -> Any:
        LOGGER.info("Processing diagnostic pcap trigger from file: %s", file_path)
        with open(file_path, "r") as f:
            payload = json.load(f)
            
        interface = payload.get("interface", "any")
        filter_str = payload.get("filter", "")
        max_duration_sec = int(payload.get("max_duration_sec", 60))
        max_bytes = int(payload.get("max_bytes", 10 * 1024 * 1024))  # Default 10MB limit
        upload_url = payload.get("upload_url")
        
        captured_chunks = []
        def caching_generator(data_generator):
            for chunk in data_generator:
                captured_chunks.append(chunk)
                yield chunk

        use_mqtt_fallback = False
        uploader = None
        
        if upload_url:
            try:
                LOGGER.info("Initiating upload connection to GCS...")
                uploader = ResumableUploader(upload_url)
                uploader.initiate_session()
            except Exception as e:
                LOGGER.warning("Failed to initiate GCS session: %s. Falling back to MQTT transport.", e)
                use_mqtt_fallback = True
        else:
            LOGGER.info("No upload_url provided. Defaulting to MQTT transport...")
            use_mqtt_fallback = True

        LOGGER.info("Starting packet sniffer...")
        data_generator = capture_packets(
            interface=interface,
            filter_str=filter_str,
            max_duration_sec=max_duration_sec,
            max_bytes=max_bytes
        )

        if not use_mqtt_fallback and uploader:
            try:
                uploader.upload_stream(caching_generator(data_generator))
                LOGGER.info("Diagnostic capture job finished successfully via HTTP.")
            except Exception as e:
                LOGGER.warning("HTTP/GCS diagnostic upload failed midway: %s. Falling back to MQTT transport...", e)
                use_mqtt_fallback = True
        else:
            # Consume packet capture generator directly to cache chunks in memory
            for chunk in data_generator:
                captured_chunks.append(chunk)

        if use_mqtt_fallback:
            LOGGER.info("Publishing PCAP capture chunks over MQTT fallback...")
            full_data = b"".join(captured_chunks)
            chunk_size = 128 * 1024  # 128KB chunks
            total_bytes = len(full_data)
            total_chunks = (total_bytes + chunk_size - 1) // chunk_size if total_bytes > 0 else 1
            session_id = f"pcap-{int(time.time())}"
            
            for idx in range(total_chunks):
                start = idx * chunk_size
                end = min(start + chunk_size, total_bytes)
                chunk_data = full_data[start:end]
                
                b64_data = base64.b64encode(chunk_data).decode()
                
                chunk_event = PcapChunkEvent(
                    session_id=session_id,
                    chunk_index=idx,
                    total_chunks=total_chunks,
                    data=b64_data
                )
                device.dispatcher.publish_event("events/pcap", chunk_event)
                LOGGER.info("Published PCAP chunk %d/%d (%d bytes)", idx + 1, total_chunks, len(chunk_data))
                
            LOGGER.info("MQTT PCAP fallback transmission completed.")
            
        return "SUCCESS"

    system_manager = device.get_manager(SystemManager)
    if system_manager:
        system_manager.register_blob_handler(
            blob_key="pcap_capture",
            process=process_pcap_blob,
            expects_file=True
        )

    LOGGER.info("Device created. Running...")
    device.run()

if __name__ == "__main__":
    main()
