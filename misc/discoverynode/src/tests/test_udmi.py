import pytest
import udmi
from unittest import mock
import time

def test_state_monitor():
  mock_publisher = mock.MagicMock()
  udmi_client = udmi.UDMI(mock_publisher)
  udmi_client.state.system = "forcechange"
  time.sleep(1)
  udmi_client.state.discovery =  "blah"
  time.sleep(1)
  assert mock_publisher.call_count == 3 # first state is published

def test_config_router():
  router = discovery.config_router.ConfigRouter()
  mock_object = mock.MagicMock()
  router.add_config_route(lambda x : x == {"test":"pass"}, mock_object)
  router.received_config({"test": "fail"})
  mock_object.take_config.assert_not_called()
  router.received_config({"test": "pass"})
  mock_object.take_config.assert_called()
