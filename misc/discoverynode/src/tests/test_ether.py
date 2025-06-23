# pylint: disable=protected-access

"""Test discovery controller's logic"""
import datetime
import logging
import sys
import time
from typing import Callable
from unittest import mock
import pytest
import udmi.discovery.ether
import udmi.schema.state as state
import udmi.schema.state
import udmi.schema.util

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


def until_true(func: Callable, message: str, timeout: int = 0):
  """Blocks until given func returns True

  Args:
    func: Predicate function
    message: Message

  Raises:
    Exception if timeout has elapsed
  """
  interval = 0.1
  expiry_time = time.monotonic() + timeout
  while time.monotonic() < expiry_time or timeout == 0:
    if func():
      return True
    time.sleep(interval)
  raise Exception(f"Timed out waiting {timeout}s for {message}")


def make_timestamp(*, seconds_from_now=0):
  return udmi.schema.util.datetime_serializer(
      udmi.schema.util.current_time_utc()
      + datetime.timedelta(seconds=seconds_from_now)
  )


def test_chain():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(mock_state, mock_publisher)

  with mock.patch.object(
      ether, "nmap_start_discovery"
  ) as mock_namp_start_discovery, mock.patch.object(
      ether, "ping_start_discovery"
  ) as mock_ping_start_discovery, mock.patch.object(
      ether, "nmap_stop_discovery"
  ) as mock_nmap_stop_discovery, mock.patch.object(
      ether, "ping_stop_discovery"
  ) as mock_ping_stop_discovery:

    ether.controller({
        "discovery": {
            "families": {
                "ether": {"generation": make_timestamp(), "depth": "ping"}
            }
        }
    })
    time.sleep(1)
    mock_namp_start_discovery.assert_not_called()
    mock_ping_start_discovery.assert_called_once()

    ether.controller({
        "discovery": {
            "families": {
                "ether": {"generation": make_timestamp(), "depth": "ports"}
            }
        }
    })
    time.sleep(1)
    mock_nmap_stop_discovery.assert_not_called()
    mock_ping_stop_discovery.assert_called_once()
    mock_ping_start_discovery.assert_called_once()
    mock_ping_start_discovery.assert_called_once()

    ether.controller({"discovery": {"families": {"ether": {}}}})
    time.sleep(1)
    mock_nmap_stop_discovery.assert_called_once()


def test_start_nmap():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(mock_state, mock_publisher)

  with mock.patch.object(
      ether, "nmap_start_discovery"
  ) as mock_namp_start_discovery, mock.patch.object(
      ether, "ping_start_discovery"
  ) as mock_ping_start_discovery:
    generation = make_timestamp()
    config = {
        "discovery": {
            "families": {"ether": {"generation": generation, "depth": "ports"}}
        }
    }

    ether.controller(config)
    time.sleep(1)
    mock_namp_start_discovery.assert_called_once()
    mock_ping_start_discovery.assert_not_called()


def test_ping_early_cancel():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(mock_state, mock_publisher)

  with mock.patch.object(
      ether, "ping_task", side_effect=lambda: time.sleep(1)
  ) as mock_ping_task:
    ether.controller({
        "discovery": {
            "families": {
                "ether": {
                    "generation": make_timestamp(),
                    "depth": "ping",
                    "addrs": list(range(1, 100)),
                }
            }
        }
    })
    time.sleep(1)

    ether._stop()

    until_true(
        lambda: ether.state.phase == udmi.schema.state.Phase.stopped,
        "discovery to stop",
        5,
    )
