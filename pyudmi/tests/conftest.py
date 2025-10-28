from unittest.mock import MagicMock
from unittest.mock import patch

import pytest


@pytest.fixture
def mock_paho_client_class():
    """
    Fixture to mock the paho.mqtt.client.Client class.

    It patches the class at its source and returns the mock class
    itself, allowing us to inspect calls to the constructor.
    """
    with patch(
        "src.udmi.core.messaging.mqtt_messaging_client.mqtt.Client") as MockClient:
        mock_instance = MagicMock()
        MockClient.return_value = mock_instance

        MockClient.mock_instance = mock_instance
        yield MockClient


@pytest.fixture
def mock_paho_client_instance(mock_paho_client_class):
    """
    A simpler fixture that just returns the mock instance
    created by the mock_paho_client_class fixture.
    """
    return mock_paho_client_class.mock_instance
