"""
Unit tests for the `LocalnetManager` class.

This module tests the `LocalnetManager` in isolation.
"""

import logging
from unittest.mock import MagicMock

import pytest

from src.udmi.core.managers.localnet_manager import LocalnetManager
from src.udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config
from udmi.schema import FamilyLocalnetConfig
from udmi.schema import LocalnetConfig
from udmi.schema import State


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def manager():
    """Returns an initialized LocalnetManager."""
    return LocalnetManager()


@pytest.fixture
def mock_provider():
    """Returns a mock FamilyProvider."""
    provider = MagicMock(spec=FamilyProvider)
    provider.validate_address = MagicMock(return_value=True)
    return provider


def _create_family_config(addr=None, devices=None):
    """Helper to create a FamilyLocalnetConfig and dynamically add attributes."""
    cfg = FamilyLocalnetConfig()
    if addr:
        cfg.addr = addr
    if devices:
        cfg.devices = devices
    return cfg


def test_register_and_get_provider(manager, mock_provider):
    """
    Verifies provider registration and retrieval.
    """
    manager.register_provider("bacnet", mock_provider)

    assert manager.get_provider("bacnet") == mock_provider
    assert manager.get_provider("unknown") is None
    assert "bacnet" in manager.get_registered_families()


def test_handle_config_builds_routing_table(manager, mock_provider):
    """
    Verifies that a valid config updates the internal routing table.
    """
    manager.register_provider("bacnet", mock_provider)

    fam_config = _create_family_config(
        addr="192.168.1.5",
        devices={
            "dev1": "1001",
            "dev2": "1002"
        }
    )

    localnet_config = LocalnetConfig(
        families={"bacnet": fam_config}
    )
    config = Config(localnet=localnet_config)

    manager.handle_config(config)

    assert manager.get_physical_address("bacnet", "dev1") == "1001"
    assert manager.get_physical_address("bacnet", "dev2") == "1002"

    family_state = manager._localnet_state.families["bacnet"]
    assert family_state.status.level == 200
    assert family_state.status.message == "Active"


def test_handle_config_validates_addresses(manager, mock_provider, caplog):
    """
    Verifies that invalid addresses are flagged and reported in status.
    """
    manager.register_provider("modbus", mock_provider)

    def validate_side_effect(addr):
        return addr != "BAD-ADDR"

    mock_provider.validate_address.side_effect = validate_side_effect

    fam_config = _create_family_config(
        devices={
            "good-dev": "1",
            "bad-dev": "BAD-ADDR"
        }
    )

    localnet_config = LocalnetConfig(
        families={"modbus": fam_config}
    )
    config = Config(localnet=localnet_config)

    with caplog.at_level(logging.WARNING):
        manager.handle_config(config)

    assert manager.get_physical_address("modbus", "good-dev") == "1"
    assert manager.get_physical_address("modbus", "bad-dev") == "BAD-ADDR"

    assert "Invalid address format" in caplog.text
    assert "BAD-ADDR" in caplog.text

    family_state = manager._localnet_state.families["modbus"]
    assert family_state.status.level == 300
    assert "Invalid address format" in family_state.status.message


def test_handle_config_unknown_family(manager, caplog):
    """
    Verifies handling of configuration for an unregistered family.
    """
    fam_config = _create_family_config(devices={"d1": "addr1"})
    localnet_config = LocalnetConfig(
        families={"unknown-fam": fam_config}
    )
    config = Config(localnet=localnet_config)

    with caplog.at_level(logging.ERROR):
        manager.handle_config(config)

    assert "unknown family" in caplog.text

    family_state = manager._localnet_state.families["unknown-fam"]
    assert family_state.status.level == 500
    assert "No provider registered" in family_state.status.message


def test_update_state_populates_localnet_block(manager):
    """
    Verifies update_state copies the internal localnet state to the main state object.
    """
    from udmi.schema import LocalnetState, FamilyLocalnetState

    fam_state = FamilyLocalnetState()
    fam_state.addr = "1.2.3.4"

    manager._localnet_state = LocalnetState(
        families={"test_fam": fam_state}
    )

    state = State()
    manager.update_state(state)

    assert state.localnet is not None
    assert "test_fam" in state.localnet.families
    assert state.localnet.families["test_fam"].addr == "1.2.3.4"
