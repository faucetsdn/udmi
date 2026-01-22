"""
Unit tests for the `JwtAuthProvider` class.

This module verifies the functionality of the `JwtAuthProvider`.
"""

from unittest.mock import MagicMock

import pytest
from freezegun import freeze_time

from udmi.core.auth import JwtAuthProvider
from udmi.core.auth.intf.signer import Signer
from udmi.core.auth.jwt_auth_provider import JwtTokenConfig


# pylint: disable=redefined-outer-name,protected-access


@pytest.fixture
def mock_signer():
    """Returns a mock Signer instance."""
    signer = MagicMock(spec=Signer)
    signer.sign.return_value = b"mock_signature"
    signer.get_algorithm_name.return_value = "RS256"
    return signer


@pytest.fixture
def jwt_provider(mock_signer):
    """Returns a JwtAuthProvider instance with a mocked signer."""
    provider = JwtAuthProvider(
        project_id="test-project",
        signer=mock_signer,
        algorithm="RS256"
    )
    return provider


def test_jwt_provider_initial_state(jwt_provider):
    """Test that the provider needs a refresh immediately upon creation."""
    assert jwt_provider.needs_refresh() is True


def test_jwt_provider_token_caching(jwt_provider):
    """Test that a token is generated and then cached."""
    with freeze_time("2025-10-17 12:00:00"):
        token1 = jwt_provider.get_password()
        assert token1 is not None
        assert jwt_provider.needs_refresh() is False

        token2 = jwt_provider.get_password()
        assert token1 == token2


def test_jwt_provider_needs_refresh_logic(mock_signer):
    """Test the time-based refresh logic."""
    jwt_provider = JwtAuthProvider(
        project_id="test-project",
        signer=mock_signer,
        algorithm="RS256",
        token_config=JwtTokenConfig(lifetime_minutes=60)
    )

    with freeze_time("2025-10-17 12:00:00"):
        token1 = jwt_provider.get_password()
        assert jwt_provider.needs_refresh() is False

    with freeze_time("2025-10-17 12:53:00"):
        assert jwt_provider.needs_refresh() is False
        assert jwt_provider.get_password() == token1  # Still cached

    with freeze_time("2025-10-17 12:55:01"):
        assert jwt_provider.needs_refresh() is True
        token2 = jwt_provider.get_password()
        assert token2 is not None
        assert token1 != token2  # New token generated
        assert jwt_provider.needs_refresh() is False  # Cached again


def test_jwt_encoding_failure(jwt_provider, caplog, mock_signer):
    """
    Tests that if the signer fails, the error propagates.
    """
    mock_signer.sign.side_effect = Exception("Signing failed")

    with pytest.raises(Exception, match="Signing failed"):
        jwt_provider.get_password()

    assert "Failed to generate new JWT" in caplog.text


def test_default_token_lifetime_is_60_minutes(mock_signer):
    """
    Tests that the default token_lifetime_minutes is 60.
    """
    with freeze_time("2025-10-17 12:00:00") as ft:
        provider = JwtAuthProvider(
            project_id="test-project",
            signer=mock_signer,
            algorithm="RS256"
        )
        provider.get_password()
        assert provider.needs_refresh() is False

        ft.move_to("2025-10-17 12:55:01")
        assert provider.needs_refresh() is True
