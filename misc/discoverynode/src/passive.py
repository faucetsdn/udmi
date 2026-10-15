"""Sample for triggering passive scan directly."""

import udmi.discovery.passive
from unittest import mock
import time
import udmi.schema.config
import udmi.schema.state
import udmi.schema.util
import sys
import logging
import datetime
import json
import argparse

state = udmi.schema.state.Discovery()

stdout = logging.StreamHandler(sys.stdout)
stdout.addFilter(lambda log: log.levelno < logging.WARNING)
stdout.setLevel(logging.INFO)
stderr = logging.StreamHandler(sys.stderr)
stderr.setLevel(logging.WARNING)
logging.root.setLevel(logging.INFO)

parser = argparse.ArgumentParser(description="subnet cidr")
parser.add_argument("subnet_filter", help="subnet_filter", type=str)
args = parser.parse_args()

a = udmi.discovery.passive.PassiveNetworkDiscovery(
    state,
    lambda x: print(x.to_json()),
    scan_id="families/ipv4",
    subnet_filter=args.subnet_filter,
)
a.apply_config(
    udmi.schema.config.DiscoveryFamily(
        generation=udmi.schema.util.datetime_serializer(
            udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=1)
        )
    )
)
while True:
  time.sleep(1)
