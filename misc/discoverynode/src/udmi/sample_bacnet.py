import logging
import multiprocessing
import sys
import time
from unittest import mock
import discovery_bacnet


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
  a = discovery_bacnet.GlobalBacnetDiscovery(state, print)
  a.controller({"discovery": {"families": {"bacnet": {"generation": "123"}}}})
  while True:
    time.sleep(1)


if __name__ == "__main__":
  multiprocessing.freeze_support()
  main()
