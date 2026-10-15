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
from udmi.schema.config import DiscoveryFamily

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
  ether = udmi.discovery.ether.EtherDiscovery(udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether")

  with mock.patch.object(
      ether, "nmap_start_discovery"
  ) as mock_namp_start_discovery, mock.patch.object(
      ether, "ping_start_discovery"
  ) as mock_ping_start_discovery, mock.patch.object(
      ether, "nmap_stop_discovery"
  ) as mock_nmap_stop_discovery, mock.patch.object(
      ether, "ping_stop_discovery"
  ) as mock_ping_stop_discovery:

    ether.apply_config(DiscoveryFamily(**{"generation": make_timestamp(), "depth": "ping"}))
    time.sleep(1)
    mock_namp_start_discovery.assert_not_called()
    mock_ping_start_discovery.assert_called_once()

    ether.apply_config(DiscoveryFamily(**{"generation": make_timestamp(), "depth": "ports"}))
    time.sleep(1)
    mock_nmap_stop_discovery.assert_not_called()
    mock_ping_stop_discovery.assert_called_once()
    mock_ping_start_discovery.assert_called_once()
    mock_namp_start_discovery.assert_called_once()

    ether.apply_config(None)
    time.sleep(1)
    mock_nmap_stop_discovery.assert_called_once()


def test_start_nmap():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether")

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

    ether.apply_config(DiscoveryFamily(**config["discovery"]["families"]["ether"]))
    time.sleep(1)
    mock_namp_start_discovery.assert_called_once()
    mock_ping_start_discovery.assert_not_called()


def test_ping_early_cancel():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether")

  with mock.patch.object(
      ether, "ping_task", side_effect=lambda _ip: time.sleep(1)
  ) as mock_ping_task:
    ether.apply_config(DiscoveryFamily(**{
                    "generation": make_timestamp(),
                    "depth": "ping",
                    "addrs": ["192.168.1.0/25"],
                }))
    until_true(
        lambda: mock_ping_task.call_count >= 1,
        "ping task to start",
        5,
    )

    ether._stop()

    until_true(
        lambda: ether.state.phase == udmi.schema.state.Phase.stopped,
        "discovery to stop",
        5,
    )
    assert mock_ping_task.call_count < 50


def test_ping_completes_with_stopped_state():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether")

  with mock.patch.object(
      ether, "ping_task", return_value=True
  ) as mock_ping_task:
    ether.apply_config(DiscoveryFamily(**{
                    "generation": make_timestamp(),
                    "depth": "ping",
                    "addrs": [f"192.168.1.{i}" for i in range(1, 10)],
                }))
    time.sleep(1)

    until_true(
        lambda: ether.state.phase == udmi.schema.state.Phase.stopped,
        "discovery to stop",
        5,
    )
    assert mock_ping_task.call_count == 9


def test_ping_netblock_expansion():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether")

  with mock.patch.object(
      ether, "ping_task", return_value=True
  ) as mock_ping_task:
    ether.apply_config(DiscoveryFamily(**{
                    "generation": make_timestamp(),
                    "depth": "ping",
                    "addrs": ["192.168.1.0/30", "10.0.0.1"],
                }))
    until_true(
        lambda: ether.state.phase == udmi.schema.state.Phase.stopped,
        "discovery to stop",
        5,
    )

    pinged_ips = {call.args[0] for call in mock_ping_task.call_args_list}
    assert pinged_ips == {"192.168.1.1", "192.168.1.2", "10.0.0.1"}


def test_nmap_adjunct_excludes_sort_index():
  mock_publisher = mock.MagicMock()
  ether = udmi.discovery.ether.EtherDiscovery(
      udmi.schema.state.Discovery(), mock_publisher, scan_id="families/ether"
  )
  ether.config = DiscoveryFamily(
      generation=make_timestamp(), depth="ports", addrs=["127.0.0.1"]
  )
  ether.generation = udmi.schema.util.current_time_utc()
  ether.count_events = 0

  fake_host = udmi.discovery.utils.nmap.NmapHost(
      ip="127.0.0.1",
      ports=[udmi.discovery.utils.nmap.NmapPort(port_number="22", state="open", protocol="tcp")],
  )
  fake_proc = mock.MagicMock()
  fake_proc.__enter__.return_value = fake_proc
  fake_proc.stdout.read.return_value = b""
  fake_proc.stdout.fileno.return_value = 0
  fake_proc.poll.return_value = 0

  with mock.patch("subprocess.Popen", return_value=fake_proc), \
       mock.patch("os.set_blocking"), \
       mock.patch("udmi.discovery.utils.nmap.results_reader", return_value=iter([fake_host])):
    ether.nmap_runner()

  host_events = [c.args[0] for c in mock_publisher.call_args_list if c.args[0].addr == "127.0.0.1"]
  assert len(host_events) == 1
  assert "sort_index" not in host_events[0].refs["22"]["adjunct"]


