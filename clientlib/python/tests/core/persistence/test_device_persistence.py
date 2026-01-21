"""
Unit tests for the `DevicePersistence` manager.

This module verifies the endpoint hierarchy logic (Active > Backup > Site).
"""

from unittest.mock import MagicMock

import pytest

from src.udmi.core.persistence import DevicePersistence
from udmi.schema import EndpointConfiguration


@pytest.fixture
def mock_backend():
    """Mocks the PersistenceBackend strategy."""
    return MagicMock()


@pytest.fixture
def site_endpoint():
    """A default 'Site' endpoint config."""
    return EndpointConfiguration(hostname="site.com", client_id="site-id")


@pytest.fixture
def persistence(mock_backend, site_endpoint):
    """Returns a DevicePersistence instance with a default site config."""
    return DevicePersistence(mock_backend, default_endpoint=site_endpoint)


def test_get_effective_endpoint_priority_active(persistence, mock_backend):
    """
    Scenario 1: Active endpoint exists.
    Expected: Returns Active.
    """
    active_conf = EndpointConfiguration(hostname="active.com")
    mock_backend.load.side_effect = lambda \
            k: active_conf.to_dict() if k == DevicePersistence.ACTIVE_KEY else None

    effective = persistence.get_effective_endpoint()

    assert effective.hostname == "active.com"


def test_get_effective_endpoint_priority_backup(persistence, mock_backend):
    """
    Scenario 2: Active missing, Backup exists.
    Expected: Returns Backup.
    """
    backup_conf = EndpointConfiguration(hostname="backup.com")

    def load_side_effect(key):
        if key == DevicePersistence.ACTIVE_KEY:
            return None
        if key == DevicePersistence.BACKUP_KEY:
            return backup_conf.to_dict()
        return None

    mock_backend.load.side_effect = load_side_effect

    effective = persistence.get_effective_endpoint()

    assert effective.hostname == "backup.com"


def test_get_effective_endpoint_priority_site(persistence, mock_backend):
    """
    Scenario 3: Active and Backup missing.
    Expected: Returns Site (Default).
    """
    mock_backend.load.return_value = None

    effective = persistence.get_effective_endpoint()

    assert effective.hostname == "site.com"
    assert effective.client_id == "site-id"


def test_get_effective_endpoint_raises_if_none_available(mock_backend):
    """
    Scenario 4: All missing (No Active, No Backup, No Site default).
    Expected: Raises ValueError.
    """
    p = DevicePersistence(mock_backend, default_endpoint=None)
    mock_backend.load.return_value = None

    with pytest.raises(ValueError, match="No effective endpoint available"):
        p.get_effective_endpoint()


def test_save_active_endpoint(persistence, mock_backend):
    """Verifies saving active endpoint also saves generation."""
    endpoint = EndpointConfiguration(hostname="new.com")
    generation = "gen_123"

    persistence.save_active_endpoint(endpoint, generation)

    mock_backend.save.assert_any_call(DevicePersistence.ACTIVE_KEY,
                                      endpoint.to_dict())
    mock_backend.save.assert_any_call(DevicePersistence.GENERATION_KEY,
                                      generation)


def test_clear_active_endpoint(persistence, mock_backend):
    """Verifies clearing active endpoint removes keys."""
    persistence.clear_active_endpoint()

    mock_backend.delete.assert_any_call(DevicePersistence.ACTIVE_KEY)
    mock_backend.delete.assert_any_call(DevicePersistence.GENERATION_KEY)
