# pylint: disable=protected-access

"""Test discovery controller's logic"""
from unittest import mock
from typing import Callable
import pytest
import udmi.schema.state as state
import udmi.discovery.discovery as discovery
import udmi.discovery.numbers
import time
import datetime
import udmi.schema.state
import udmi.schema.util 
import logging
import sys

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


def make_timestamp(*,seconds_from_now = 0):
  return udmi.schema.util.datetime_serializer(udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=seconds_from_now))


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


def test_number_discovery_start_and_stop():
  mock_state = mock.MagicMock()
  mock_publisher = mock.MagicMock()
  numbers = udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  numbers._start()
  assert numbers.state.phase == state.Phase.active
  time.sleep(5)
  numbers._stop()
  assert numbers.state.phase == state.Phase.stopped
  #until_true(lambda: numbers.state.phase == discovery.states.FINISHED, "phase to be finished", 8)
  # maybe flakey?
  assert [1, 2, 3, 4, 5] == [x[0].scan_addr for (x, _) in mock_publisher.call_args_list]


def test_event_counts():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  numbers = udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  numbers._start()
  time.sleep(5)
  numbers._stop()
  assert mock_publisher.call_count == 5
  assert [1, 2, 3, 4, 5] == [x[0].event_no for (x, _) in mock_publisher.call_args_list]
  
  numbers.on_state_update_hook()

  assert mock_state.discovery.families["vendor"].active_count == 5




def test_add_discovery_block_triggers_discovery_start():
  mock_state = mock.MagicMock()
  mock_publisher = mock.MagicMock()
  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  with (
    mock.patch.object(numbers, "start_discovery") as mock_start,
  ):
    numbers.controller({"discovery": {"families": {"vendor" : {"generation": make_timestamp()}}}})
    print(mock_state)
    time.sleep(1)
    mock_start.assert_called()
  
def test_having_no_config_then_recieve_repeated_identical_configs():
  mock_state = mock.MagicMock()
  mock_publisher = mock.MagicMock()
  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)

  generation_timestamp = make_timestamp()

  with mock.patch.object(numbers, "start_discovery") as mock_start:
    
    for _ in range(5):
      numbers.controller({"discovery": {"families": {"vendor" : {"generation": generation_timestamp}}}})
      time.sleep(1)
    
    mock_start.assert_called_once()

def test_past_generation():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()

  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  generation = make_timestamp(seconds_from_now = -8)
  numbers.controller({"discovery": {"families": {"vendor" : {"generation": generation}}}})
  time.sleep(3)

  assert mock_state.discovery.families["vendor"].generation == generation
  assert all(x[0].generation == generation for (x, _) in mock_publisher.call_args_list)
  

def test_stopping_completed_discovery():
  # should not go through "stopping" because it's done .. i.e. ignore!
  pass

def test_invalid_duration_and_interval():
  return
  # should not go through "stopping" because it's done .. i.e. ignore!
  mock_state = mock.MagicMock()
  mock_publisher = mock.MagicMock()
  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  with mock.patch.object(numbers, "start_discovery") as mock_start:
    numbers.controller({"discovery": {"families": {"number" : {"generation": "ts", "scan_interval_sec": 1, "scan_duration_sec": 10}}}})
    assert numbers.state.phase == discovery.states.error
    assert numbers.state.status.level == 500
    mock_start.assert_not_called()
