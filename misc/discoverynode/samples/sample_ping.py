"""Sample for triggering passive scan directly."""

import udmi.discovery.ether
from unittest import mock
import time
import udmi.schema.util
import sys
import logging
import datetime

state = mock.MagicMock()

stdout = logging.StreamHandler(sys.stdout)
stdout.addFilter(lambda log: log.levelno < logging.WARNING)
stdout.setLevel(logging.INFO)
stderr = logging.StreamHandler(sys.stderr)
stderr.setLevel(logging.WARNING)
logging.basicConfig(
    format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
    handlers=[stderr, stdout],
    level=logging.INFO,
)
logging.root.setLevel(logging.DEBUG)

a = udmi.discovery.ether.EtherDiscovery(state, print)
a.controller(
    {
        "discovery": {
            "families": {
                "ether": {
                    "generation": udmi.schema.util.datetime_serializer(
                        udmi.schema.util.current_time_utc()
                        + datetime.timedelta(seconds=1)
                    ),
                    "depth": "ping",
                    "addrs": ["127.0.0.1", "8.8.8.8"],
                }
            }
        }
    }
)
while True:
    time.sleep(1)
