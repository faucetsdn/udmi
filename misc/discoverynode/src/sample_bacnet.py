<<<<<<< HEAD
"""Sample for calling BACnet scan directly"""
=======
>>>>>>> 82d5a00d (wip)
import logging
import multiprocessing
import sys
import time
from unittest import mock
<<<<<<< HEAD
import udmi.discovery.bacnet

=======
import udmi.discovery.nmap
import json
>>>>>>> 82d5a00d (wip)

def main():
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
  logging.root.setLevel(logging.INFO)
  state = mock.MagicMock()
  a = udmi.discovery.bacnet.GlobalBacnetDiscovery(
      state, print, bacnet_ip=None
  )
  a.controller({"discovery": {"families": {"bacnet": {"generation": "123"}}}})
  while True:
    time.sleep(1)


  def pp(obj):
    print(obj.to_json())

  a = udmi.discovery.nmap.NmapBannerScan(state, print, target_ips=["127.0.0.1"])
  a._start()
  time.sleep(30)

if __name__ == "__main__":
  multiprocessing.freeze_support()
  main()
