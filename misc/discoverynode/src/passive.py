"""Sample for triggering passive scan directly."""

import udmi.discovery.passive
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
logging.root.setLevel(logging.INFO)

a = udmi.discovery.passive.PassiveNetworkDiscovery(state, print)
a.controller(
  {
    "discovery": {
      "families": {
        "ipv4": {
          "generation": udmi.schema.util.datetime_serializer(
            udmi.schema.util.current_time_utc()
            + datetime.timedelta(seconds=1)
          )
        }
      }
    }
  }
)
while True:
  time.sleep(1)
