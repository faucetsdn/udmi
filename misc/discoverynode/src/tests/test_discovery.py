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
import collections
import math

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

class MockedDateTime(datetime.datetime):
  @classmethod
  def now(cls, tz=None):
    return datetime.datetime.fromtimestamp(1000, tz=tz)

def make_timestamp(*,seconds_from_now = 0, seconds_from_epoch = None):
  if seconds_from_epoch:
    return udmi.schema.util.datetime_serializer(datetime.datetime.fromtimestamp(seconds_from_epoch))
  else:
    return udmi.schema.util.datetime_serializer(udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=seconds_from_now))


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
  assert [None, '1', '2', '3', '4', '5', None] == [x[0].addr for (x, _) in mock_publisher.call_args_list]


def test_event_counts():
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()
  numbers = udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  numbers._start()
  time.sleep(5)
  numbers._stop()
  # Because of "negative" start and end markers
  assert mock_publisher.call_count == 7
  
  #assert [1, 2, 3, 4, 5] == [x[0].event_no for (x, _) in mock_publisher.call_args_list]
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
  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher, range=None)

  generation_timestamp = make_timestamp()

  with mock.patch.object(numbers, "start_discovery") as mock_start:
    
    for _ in range(5):
      numbers.controller({"discovery": {"families": {"vendor" : {"generation": generation_timestamp}}}})
      time.sleep(1)
    
    mock_start.assert_called_once()

def test_past_generation_within_tolerance():
  # Check that a slightly past generation still triggers discovery
  # and that the generation in messages is the actual generation timestamp 
  mock_state = udmi.schema.state.State()
  mock_publisher = mock.MagicMock()

  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)
  generation = make_timestamp(seconds_from_now = -8)
  numbers.controller({"discovery": {"families": {"vendor" : {"generation": generation}}}})
  time.sleep(3)

  # generation in state/event classes is a datetime whereas `generation` in a config is a string
  assert udmi.schema.util.datetime_serializer(mock_state.discovery.families["vendor"].generation) == generation
  assert all(udmi.schema.util.datetime_serializer(x[0].generation) ==  generation for (x, _) in mock_publisher.call_args_list)


@pytest.mark.parametrize(
  "seconds_from_now, scan_interval, expected_delay, threshold, should_raise_error",
  [
    (-18, 10, 2, -1, False),  # In the past, next run in 2 seconds
    (-20, 10, 0, -1, False),  # In the past, next run is now
    (-22, 10, 8, -1, False),  # In the past, next run in 8 seconds
    (10, 10, 10, -1, False),   # In the future, runs in 10 seco   nds
    (-5, None, -5, -10, False), # In the past, no interval, starts with negative delay
  #  (-11, None, None, True), # In the past, no interval, exceeds threshold
  ]
)
def test_generation_scheduling(seconds_from_now, scan_interval, threshold, expected_delay, should_raise_error):
    # Note: 
    mock_state = udmi.schema.state.State()
    mock_publisher = mock.MagicMock()
    numbers = udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)

    with mock.patch.object(numbers, '_scheduler') as mock_scheduler, \
         mock.patch.object(discovery, 'MAX_THRESHOLD_GENERATION', new=threshold), \
         mock.patch('time.time', return_value=1000), \
         mock.patch('datetime.datetime', MockedDateTime):
        logging.error(f"seconds from now: {seconds_from_now}")
        generation_timestamp = make_timestamp(seconds_from_epoch=1000 + seconds_from_now) 
        config = {
            "discovery": {
                "families": {
                    "vendor": {
                        "generation": generation_timestamp,
                        "scan_interval_sec": scan_interval if scan_interval else None
                    }
                }
            }
        }

        if should_raise_error:
            # doesn't work because wrapped
            assert numbers.state.phase == udmi.schema.state.Phase.stopped
            return

        numbers.controller(config)
        mock_scheduler.assert_called()
        call_args, _ = mock_scheduler.call_args
        start_time = call_args[0]
      
    # hacky because timestamps are generted from datetime and timestamp
    # needs to mock all the objects to make it work
    assert pytest.approx(expected_delay + 1000, abs=1) == start_time

@pytest.mark.parametrize(
  "scan_duration, scan_interval",
  [
    # Set to more than one second to avoid issues with state message publishing
    (None, 1),
    (None, 3),
    (1, 3)
  
  ]
)
def test_generation_is_incremented(scan_duration, scan_interval):
  mock_state = state.State()
  mock_publisher = mock.MagicMock()
  numbers =  udmi.discovery.numbers.NumberDiscovery(mock_state, mock_publisher)

  cycles = 5
  got = set()
  initial_generation = udmi.schema.util.current_time_utc() 

  with (
    mock.patch.object(numbers, "start_discovery") as mock_start,
  ):
    
    numbers.controller({"discovery": {
      "families": {
        "vendor" : {
          "generation": udmi.schema.util.datetime_serializer(initial_generation), 
          "scan_interval_sec": scan_interval, 
          "scan_duration_sec": scan_duration
          }
        }
      }
    })

    for x in range(cycles * scan_interval * 10 + 5):
      got.add(udmi.schema.util.datetime_serializer(mock_state.discovery.families["vendor"].generation))
      time.sleep(0.1)
    
    want = set([udmi.schema.util.datetime_serializer(initial_generation +  datetime.timedelta(seconds=scan_interval * n)) for n in range(cycles)])
    assert mock_start.call_count == cycles
    assert want == got
