import logging
import sys
import time
import tomllib
import discovery_bacnet
import discovery_nmap
import discovery_numbers
import discovery_passive
import mqtt
import udmi
import multiprocessing

# TODO: Move into env variable
CONFIG_FILE_PATH = "config.toml"

def load_config_from_file(file_name: str):
  with open(file_name, "rb") as f:
    return tomllib.load(f)


def make_udmi_client(config, mclient):
   # Initialise UDMI Agent
  udmi_client = udmi.UDMI(
      mqtt_client=mclient, device_id=config["mqtt"]["device_id"]
  )

  mclient.set_config_callback(udmi_client.config_handler)
  mclient.start_client()

  passive_discovery = discovery_passive.PassiveNetworkDiscovery(
      udmi_client.state, udmi_client.publish_discovery
  )
  number_discovery = discovery_numbers.NumberDiscovery(
      udmi_client.state, udmi_client.publish_discovery
  )
  bacnet_discovery = discovery_bacnet.GlobalBacnetDiscovery(
      udmi_client.state,
      udmi_client.publish_discovery,
      bacnet_ip=config["bacnet"]["ip"],
  )
  nmap_banner_scan = discovery_nmap.NmapBannerScan(
      udmi_client.state,
      udmi_client.publish_discovery,
      target_ips=config["nmap"]["targets"],
  )

  udmi_client.add_config_route(
      lambda x: number_discovery.scan_family
      in x.get("discovery", {}).get("families", {}),
      number_discovery,
  )
  udmi_client.add_config_route(
      lambda x: bacnet_discovery.scan_family
      in x.get("discovery", {}).get("families", {}),
      bacnet_discovery,
  )

  # THESE TWO USE THE SAME SCAN_FAMILY
  # THEIR `states` IN THE OVERALL STATE CLASHES
  # because both set state.discovery.families.SCAN_FAMILY = self.state
  udmi_client.add_config_route(
      lambda x: passive_discovery.scan_family
      in x.get("discovery", {}).get("families", {}),
      passive_discovery,
  )
  udmi_client.add_config_route(
      lambda x: nmap_banner_scan.scan_family
      in x.get("discovery", {}).get("families", {})
      and x["discovery"]["families"][nmap_banner_scan.scan_family].get("depths")
      == "banner",
      nmap_banner_scan,
  )

  return udmi_client

def main():
  
  stdout = logging.StreamHandler(sys.stdout)
  stdout.addFilter(lambda log: log.levelno < logging.WARNING)
  stdout.setLevel(logging.INFO)
  stderr = logging.StreamHandler(sys.stderr)
  stderr.setLevel(logging.WARNING)
  logging.basicConfig(
      format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
      handlers=[stderr, stdout],
      level=logging.INFO
  )
  logging.root.setLevel(logging.INFO)

  logging.info("Loading config from %s", CONFIG_FILE_PATH)
  config = load_config_from_file(CONFIG_FILE_PATH)

  # Initialise (but not start) the MQTT Client
  mclient = mqtt.MQTTClient(
      device_id=config["mqtt"]["device_id"],
      registry_id=config["mqtt"]["registry_id"],
      region=config["mqtt"]["region"],
      project_id=config["mqtt"]["project_id"],
      hostname=config["mqtt"]["host"],
      port=config["mqtt"]["port"],
      key_file=config["mqtt"]["key_file"],
      algorithm=config["mqtt"]["algorithm"],
  )

  udmi_client = make_udmi_client(config, mclient)

  while True:
    time.sleep(0.000001)
    # message = mclient.publish_message("/devices/AHU-1/events", "test")
    # message.wait_for_publish()
    # logging.info(f"published message {message}")


if __name__ == "__main__":
  multiprocessing.freeze_support()
  main()
