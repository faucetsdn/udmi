"""Sample for triggering passive scan directly."""

import udmi.discovery.ether
from unittest import mock
import time
import udmi.schema.util
import sys
import logging
import datetime
import argparse

state = mock.MagicMock()

stdout = logging.StreamHandler(sys.stdout)
stdout.addFilter(lambda log: log.levelno < logging.WARNING)
stdout.setLevel(logging.INFO)
stderr = logging.StreamHandler(sys.stderr)
stderr.setLevel(logging.WARNING)
logging.root.setLevel(logging.INFO)

parser = argparse.ArgumentParser(description="ping targets")
parser.add_argument("targets", help="nmap target", type=str)
args = parser.parse_args()

a = udmi.discovery.ether.EtherDiscovery(state, print)
a.controller(
  {
    "discovery": {
      "families": {
        "ether": {
          "generation": udmi.schema.util.datetime_serializer(
            udmi.schema.util.current_time_utc()
            + datetime.timedelta(seconds=1)),
            "addrs": args.targets.split(","),
            "depth": "ports"
        }
      }
    }
  }
)

while True:
  time.sleep(1)
