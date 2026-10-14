"""Sample for triggering services scan directly."""

import argparse
import datetime
import json
import logging
import sys
import time
from unittest import mock
import udmi.discovery.ether
import udmi.schema.util

state = mock.MagicMock()

stdout = logging.StreamHandler(sys.stdout)
stdout.addFilter(lambda log: log.levelno < logging.WARNING)
stdout.setLevel(logging.INFO)
stderr = logging.StreamHandler(sys.stderr)
stderr.setLevel(logging.WARNING)
logging.root.setLevel(logging.INFO)

parser = argparse.ArgumentParser(description="services scan targets")
parser.add_argument("targets", help="nmap target", type=str)
args = parser.parse_args()

a = udmi.discovery.ether.EtherDiscovery(state, lambda x: print(x.to_json()))
a.controller(
  {
    "discovery": {
      "families": {
        "ether": {
          "generation": udmi.schema.util.datetime_serializer(
            udmi.schema.util.current_time_utc()
            + datetime.timedelta(seconds=1)),
            "addrs": args.targets.split(","),
            "depth": "services"
        }
      }
    }
  }
)

while True:
  time.sleep(1)
