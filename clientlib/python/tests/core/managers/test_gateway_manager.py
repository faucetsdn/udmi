"""
Unit tests for the `GatewayManager` class.

This module tests the `GatewayManager` in isolation by mocking the
Device, Persistence, and Dispatcher.
"""

import logging
from unittest.mock import MagicMock
from unittest.mock import call

import pytest

from src.udmi.core.managers.gateway_manager import GatewayManager
from src.udmi.core.managers.localnet_manager import LocalnetManager
from udmi.schema import Config
from udmi.schema import FamilyLocalnetModel
from udmi.schema import GatewayConfig


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_dispatcher():
    """Mocks the MessageDispatcher and its internal client."""
    dispatcher = MagicMock()
    dispatcher.client = MagicMock()
    return dispatcher


@pytest.fixture
def mock_persistence():
    """Mocks the DevicePersistence."""
    return MagicMock()


@pytest.fixture
def mock_device(mock_persistence):
    """Mocks the parent Device object."""
    device = MagicMock()
    device.persistence = mock_persistence
    # Default behavior: No LocalnetManager returned unless specified
    device.get_manager.return_value = None
    return device


@pytest.fixture
def manager(mock_device, mock_dispatcher):
    """Returns an initialized GatewayManager wired to mocks."""
    mgr = GatewayManager()
    mgr.set_device_context(device=mock_device, dispatcher=mock_dispatcher)
    return mgr


def test_start_restores_proxies_from_persistence(manager, mock_device,
    mock_dispatcher):
    """
    Verifies that start() loads saved proxies and re-sends 'attach' messages
    to ensure the cloud knows they are connected.
    """
    mock_device.persistence.get.return_value = ["proxy-1", "proxy-2"]

    manager.start()

    assert "proxy-1" in manager._proxies
    assert "proxy-2" in manager._proxies

    publish = mock_dispatcher.client.publish
    expected_calls = [
        call("attach", "", device_id="proxy-1"),
        call("attach", "", device_id="proxy-2")
    ]
    publish.assert_has_calls(expected_calls, any_order=True)


def test_add_proxy_flow(manager, mock_device, mock_dispatcher):
    """
    Verifies adding a proxy:
    1. Updates internal list.
    2. Persists list.
    3. Sends 'attach' message.
    4. Subscribes to proxy topics.
    """
    manager.add_proxy("new-proxy")

    assert "new-proxy" in manager._proxies

    mock_device.persistence.set.assert_called_with(
        GatewayManager.PERSISTENCE_KEY, ["new-proxy"]
    )

    client = mock_dispatcher.client
    client.publish.assert_called_with("attach", "", device_id="new-proxy")

    expected_subs = [
        call("config", "new-proxy"),
        call("commands/#", "new-proxy")
    ]
    client.register_channel_subscription.assert_has_calls(expected_subs,
                                                          any_order=True)


def test_remove_proxy_flow(manager, mock_device, mock_dispatcher):
    """
    Verifies removing a proxy:
    1. Updates internal list.
    2. Persists list.
    3. Sends 'detach' message.
    """
    manager._proxies.append("old-proxy")

    manager.remove_proxy("old-proxy")

    assert "old-proxy" not in manager._proxies

    mock_device.persistence.set.assert_called_with(
        GatewayManager.PERSISTENCE_KEY, []
    )

    mock_dispatcher.client.publish.assert_called_with(
        "detach", "", device_id="old-proxy"
    )


def test_handle_config_syncs_proxies(manager):
    """
    Verifies that the GatewayManager reconciles the cloud config
    proxy list with its internal list.
    """
    manager._proxies = ["proxy-A", "proxy-B"]

    gw_config = GatewayConfig(proxy_ids=["proxy-B", "proxy-C"])
    config = Config(gateway=gw_config)

    manager.add_proxy = MagicMock()
    manager.remove_proxy = MagicMock()

    manager.handle_config(config)

    manager.remove_proxy.assert_called_once_with("proxy-A")
    manager.add_proxy.assert_called_once_with("proxy-C")


def test_validation_success(manager, mock_device):
    """
    Verifies that if the target family matches a registered provider,
    the status is cleared/OK.
    """
    mock_localnet = MagicMock(spec=LocalnetManager)
    mock_localnet.get_registered_families.return_value = ["bacnet"]
    mock_device.get_manager.return_value = mock_localnet

    config = Config(
        gateway=GatewayConfig(
            target=FamilyLocalnetModel(family="bacnet")
        )
    )

    manager.handle_config(config)

    if manager._gateway_state.status:
        assert manager._gateway_state.status.level < 500


def test_validation_failure_unsupported_family(manager, mock_device):
    """
    Verifies that if the target family is NOT supported,
    an Error status (500) is reported.
    """
    mock_localnet = MagicMock(spec=LocalnetManager)
    mock_localnet.get_registered_families.return_value = ["modbus"]
    mock_device.get_manager.return_value = mock_localnet

    config = Config(
        gateway=GatewayConfig(
            target=FamilyLocalnetModel(family="bacnet")
        )
    )

    manager.handle_config(config)

    assert manager._gateway_state.status is not None
    assert manager._gateway_state.status.level == 500
    assert "not supported" in manager._gateway_state.status.message


def test_proxy_message_routing(manager):
    """
    Verifies that messages for a specific proxy are routed to the
    correct callback handler.
    """
    mock_config_handler = MagicMock()
    mock_command_handler = MagicMock()

    manager.add_proxy("proxy-1",
                      config_handler=mock_config_handler,
                      command_handler=mock_command_handler)

    config_obj = Config(timestamp="now")
    manager.handle_proxy_config("proxy-1", config_obj)
    mock_config_handler.assert_called_once_with("proxy-1", config_obj)

    payload = {"cmd": "reset"}
    manager.handle_proxy_command("proxy-1", "reset", payload)
    mock_command_handler.assert_called_once_with("proxy-1", "reset", payload)


def test_proxy_routing_exception_safety(manager, caplog):
    """
    Verifies that if a proxy handler crashes, it is logged and doesn't
    crash the manager.
    """
    mock_handler = MagicMock(side_effect=ValueError("Proxy Crash"))
    manager.add_proxy("proxy-1", config_handler=mock_handler)

    with caplog.at_level(logging.ERROR):
        manager.handle_proxy_config("proxy-1", Config())

    assert "Error in proxy config handler" in caplog.text
    assert "Proxy Crash" in caplog.text
