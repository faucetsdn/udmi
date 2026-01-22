"""
Unit tests for the `DiscoveryManager` class.

This module tests the `DiscoveryManager` in isolation by mocking the
Device, Dispatcher, and LocalnetManager/Providers.
"""

import logging
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.managers.base_manager import BaseManager
from src.udmi.core.managers.discovery_manager import DiscoveryManager
from src.udmi.core.managers.localnet_manager import LocalnetManager
from src.udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config
from udmi.schema import DiscoveryConfig
from udmi.schema import DiscoveryEvents
from udmi.schema import Enumerations
from udmi.schema import FamilyDiscoveryConfig
from udmi.schema import State


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_dispatcher():
    """Mocks the MessageDispatcher."""
    dispatcher = MagicMock()
    return dispatcher


@pytest.fixture
def mock_localnet():
    """Mocks the LocalnetManager."""
    localnet = MagicMock(spec=LocalnetManager)
    localnet.get_registered_families.return_value = ["bacnet", "ethernet"]
    return localnet


@pytest.fixture
def mock_device(mock_localnet):
    """Mocks the parent Device object."""
    device = MagicMock()
    device.get_manager.return_value = mock_localnet
    return device


@pytest.fixture
def manager(mock_device, mock_dispatcher):
    """Returns an initialized DiscoveryManager wired to mocks."""
    mgr = DiscoveryManager()
    mgr.set_device_context(device=mock_device, dispatcher=mock_dispatcher)
    return mgr


@pytest.fixture
def mock_provider():
    """Returns a mock FamilyProvider."""
    return MagicMock(spec=FamilyProvider)


def test_handle_config_triggers_enumeration(manager, mock_dispatcher,
    mock_localnet):
    """
    Verifies that if config.discovery.enumerations is present, the manager
    publishes a discovery event with capabilities.
    """
    mock_localnet.get_registered_families.return_value = ["bacnet", "ethernet"]

    config = Config(
        discovery=DiscoveryConfig(enumerations=Enumerations())
    )

    manager.handle_config(config)

    mock_dispatcher.publish_event.assert_called_once()
    call_args = mock_dispatcher.publish_event.call_args

    args, _ = call_args
    channel = args[0]
    event = args[1]

    assert channel == "events/discovery"
    assert isinstance(event, DiscoveryEvents)
    assert "bacnet" in event.families
    assert "ethernet" in event.families


def test_scheduled_scan_trigger(manager, mock_localnet, mock_provider):
    """
    Verifies that _check_scheduled_scans triggers a scan if the interval has passed.
    """
    family_name = "test_fam"
    mock_localnet.get_registered_families.return_value = [family_name]
    mock_localnet.get_provider.return_value = mock_provider

    fam_config = FamilyDiscoveryConfig(scan_interval_sec=60)
    manager._config = DiscoveryConfig(families={family_name: fam_config})

    manager._last_scan_times[family_name] = 0

    with patch("threading.Thread") as mock_thread_cls:
        manager._check_scheduled_scans()

        mock_thread_cls.assert_called_once()
        call_kwargs = mock_thread_cls.call_args.kwargs
        assert call_kwargs['args'][0] == family_name
        assert call_kwargs['args'][1] == mock_provider


def test_handle_discovery_command(manager, mock_localnet, mock_provider):
    """
    Verifies that the 'discovery' command triggers an immediate scan.
    """
    mock_localnet.get_provider.return_value = mock_provider

    payload = {"families": ["bacnet"]}

    with patch("threading.Thread") as mock_thread_cls:
        manager.handle_command("discovery", payload)

        mock_thread_cls.assert_called_once()
        call_kwargs = mock_thread_cls.call_args.kwargs
        assert call_kwargs['args'][0] == "bacnet"


def test_scan_execution_flow(manager, mock_provider, mock_dispatcher):
    """
    Verifies the _run_scan logic:
    1. Calls provider.start_scan.
    2. Provider calls callback.
    3. Manager publishes event.
    """
    family = "bacnet"

    def side_effect_start_scan(config, callback):
        event = DiscoveryEvents(addr="1234")
        callback("scan_id_1", event)

    mock_provider.start_scan.side_effect = side_effect_start_scan

    manager._run_scan(family, mock_provider)

    mock_provider.start_scan.assert_called_once()

    mock_dispatcher.publish_event.assert_called_once()

    args, kwargs = mock_dispatcher.publish_event.call_args
    assert args[0] == "events/discovery"

    assert args[2] == "scan_id_1"
    assert args[1].addr == "1234"


def test_scan_exception_handling(manager, mock_provider, caplog):
    """
    Verifies that if provider.start_scan raises an exception, it is caught
    and logged.
    """
    mock_provider.start_scan.side_effect = RuntimeError("Scan Crash")

    with caplog.at_level(logging.ERROR):
        manager._run_scan("bacnet", mock_provider)

    assert "Scan failed for 'bacnet'" in caplog.text
    assert "Scan Crash" in caplog.text


def test_active_scan_state_update(manager):
    """
    Verifies that the discovery state reflects active scanning.
    """
    family = "bacnet"

    manager._update_family_state(family, active=True)
    state = State()
    manager.update_state(state)

    fam_state = state.discovery.families[family]
    assert getattr(fam_state, 'active') is True

    manager._update_family_state(family, active=False)
    state_after = State()
    manager.update_state(state_after)
    assert getattr(state_after.discovery.families[family], 'active') is False


def test_stop_cancels_providers(manager, mock_provider):
    """
    Verifies that manager.stop() calls stop_scan() on active providers.
    """
    manager._active_providers.append(mock_provider)

    with patch.object(BaseManager, "stop"):
        manager.stop()

    mock_provider.stop_scan.assert_called_once()
    assert len(manager._active_providers) == 0
