"""
Pytest fixtures for testing the MQTT messaging client.
"""

from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from udmi.schema import EndpointConfiguration


@pytest.fixture
def mock_paho_client_class():
    """
    Fixture to mock the paho.mqtt.client.Client class.

    It patches the class at its source and returns the mock class
    itself, allowing us to inspect calls to the constructor.
    """
    with patch(
        "src.udmi.core.messaging.mqtt_messaging_client.mqtt.Client") as mock_client:
        mock_instance = MagicMock()
        mock_client.return_value = mock_instance

        mock_client.mock_instance = mock_instance
        yield mock_client


@pytest.fixture
def mock_paho_client_instance(
    mock_paho_client_class  # pylint: disable=redefined-outer-name
):
    """
    A simpler fixture that just returns the mock instance
    created by the mock_paho_client_class fixture.
    """
    return mock_paho_client_class.mock_instance


@pytest.fixture
def mock_auth_provider():
    """
    Mock a basic auth provider.
    """
    provider = MagicMock()
    provider.get_username.return_value = "unused"
    provider.get_password.return_value = "mock_password"
    provider.needs_refresh.return_value = False
    return provider


@pytest.fixture
def mock_endpoint_config():
    """A mock EndpointConfiguration object."""
    return EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )
