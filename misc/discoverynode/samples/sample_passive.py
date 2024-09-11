"""Sample for triggering passive scan directly."""
import discovery_passive
from unittest import mock
import time
state = mock.MagicMock()
a = discovery_passive.PassiveNetworkDiscovery(state, print)
a.controller({"discovery": {"families": {"ipv4" : {"generation": "123"}}}})
while True:
  time.sleep(1)
