import argparse
import logging
from udmi.schema import EndpointConfiguration, AuthProvider, Basic
from udmi.core.logging.mqtt_handler import UDMIMqttLogHandler
from spotter.core.device import SpotterDevice

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("SpotterMain")

def main():
    parser = argparse.ArgumentParser(description="Spotter - UDMI Python Reference Client")
    parser.add_argument("--client_id", required=True, help="MQTT Client ID")
    parser.add_argument("--hostname", required=True, help="MQTT Broker Hostname")
    parser.add_argument("--port", type=int, default=8883, help="MQTT Broker Port")
    parser.add_argument("--topic_prefix", default="", help="MQTT Topic Prefix")

    # Basic Auth
    parser.add_argument("--username", help="MQTT Username")
    parser.add_argument("--password", help="MQTT Password")

    # JWT Auth
    parser.add_argument("--jwt_audience", help="JWT Audience")
    parser.add_argument("--key_file", help="Path to RSA/ES private key file for JWT Auth")

    args = parser.parse_args()

    auth_provider = None
    if args.username and args.password:
        auth_provider = AuthProvider(basic=Basic(username=args.username, password=args.password))
    elif args.jwt_audience:
        auth_provider = AuthProvider(jwt={"audience": args.jwt_audience})

    endpoint = EndpointConfiguration(
        client_id=args.client_id,
        hostname=args.hostname,
        port=args.port,
        topic_prefix=args.topic_prefix,
        auth_provider=auth_provider
    )

    spotter = SpotterDevice(endpoint, key_file=args.key_file)

    # Attach UDMI MQTT Log Handler to root logger to route all logs (including exceptions)
    # to the cloud via 'events/system' telemetry stream.
    mqtt_log_handler = UDMIMqttLogHandler(spotter.sys_manager)
    logging.getLogger().addHandler(mqtt_log_handler)

    try:
        spotter.run()
    except SystemExit:
        LOGGER.info("Spotter shutdown successfully (Restart Triggered).")
    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")

if __name__ == "__main__":
    main()
