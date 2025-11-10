"""
Unit tests for the `JwtAuthProvider` class.

This module verifies the functionality of the `JwtAuthProvider` by mocking
the private key file.

Key behaviors verified:
- Token Generation: Ensures a JWT is created correctly.
- Token Caching: Confirms that a generated token is cached and reused.
- Refresh Logic: Uses `freezegun` to test the time-based refresh
  logic, ensuring `needs_refresh()` returns True only after the
  refresh buffer period has passed.
- Error Handling:
    - Checks for `FileNotFoundError` if the key file is missing.
    - Checks for `PyJWTError` (or other lib-specific errors) if
      JWT encoding fails, ensuring it returns a safe value.
- Defaults: Verifies the default token lifetime is 60 minutes.
"""

from unittest.mock import mock_open
from unittest.mock import patch

import pytest
from freezegun import freeze_time
from jwt.exceptions import PyJWTError

from src.udmi.core.auth import JwtAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtTokenConfig

MOCK_PRIVATE_KEY = """-----BEGIN PRIVATE KEY-----
MIIEugIBADANBgkqhkiG9w0BAQEFAASCBKQwggSgAgEAAoIBAQCe5RIBonQNXte4
T6rTTaPzYk92NRTMFCFt1T7aJmBh5q19VCaMtsnSBr602bPMqOOlufwXftYdc9/g
jxKRYG/+9WNH0YSk5ZAB1eDUGInuDECF+43aSd9t8z3wQ6W7hDAy4cM74rR0YZoY
dC2SKo5dsKdDln0XhkoY0nzxFfMoU1udTvHUxIHoCH070rE+zXq+KsKBYW4M5pCn
JVQw4x/sJNDYJE4O4qIF8kNCfU8M2i0Zet3fRKrg7brxlEWt0rNGTLGwuSfgi+hL
t1UH5wxA1vk/v1YhGMjiY2gh4f58Wl/l0nGet4AfIxrbeNP86OhPd7d6RmKgBs3K
YjHgjNxpAgMBAAECgf8agLRmkWVjolSMHL2uTcTxU44gqVOB6dmpkc5p+vyyJHr+
uVdOVGHu8v3cLHO3YnQvsFXb+8vuHbKGqIm9HD8rlFwUhPeBVmKVHv3hL2nSMTj4
U0nV0klYjYatpjR+knREbsoIGXtmVHrfZhsAkxiSVMsq+tVI/Z0rIFcdEnKV+kFG
hMWwGabklVdWVl9NhAFk7TBhi/BCDyHRh4apXWjnTN/UCqRSq3TnR6hUMH+RFXP4
nLn6W5EDGTvkZMjbi2N1saW08Eet8dDpc0KCyH8uzoDTUMZE27YpQ3BiEJAfNnZA
0pjBI95Gi0riOuzw39Y8uFFnx2A53c2rXgK1xzkCgYEA0VyNDf2vN6FDMQYM23xt
xmll+ZLOOiPIWx4rS774m+rD4CjMeGOvHHn8u4I3plImeW6izsOF3N/r+kBodoDD
1mLIx6LPg9th6IKZHWTndiWtJHjplbasK2uM8Slp5lgh+UqR9HGt9fzv5kd34z0v
g1Sjl050amAm8WsUnUyGdL0CgYEAwkqCDcSpvDqRiKOdm/abAjYAPTIbx14Ke9CN
QEFolsfDZWwZqYQpc7i0alJGsfwoffrKSLjq512LXmyoprMtwxVhsuShLTRhLs9Q
v10x5qWia6qLaPodl/OwsXIm4aoPur63fxyhFbsBormjW6OL9pjDm4XE5G+VV6k4
t3y13x0CgYBKPqEDCii9+KFEXFno6Cphx3TTWb1qns/piDxgYzv0xv9pme5Q70Dl
P1liAZ7Mi6t7OsHfwzTQjFQ57eddxvIsL+H18gXXQ9BnzRCRfPwcSOYq3fowDpTl
3QEhcMdOWYjKyCgUYNAJQQauSj/4xPT5hOVyve7I3opQ6OFpmv6/nQKBgB8ob77h
gQ7snZY6FvaCD83E9zjZJldMn4XHpj9dvfYgRxemxF7JERS71EMQusGkXKIHbBVJ
gnJkMAuOhWVmarpUXXyg6bAkLEmjeaGWwn/opCl8CitloQVTUUzDr7SU3zzlhOvf
nX2spdCA0M/LZJDBYu04AKFZu6t5Od1DJK+JAoGAXBeCLR3k/pyojqGFKX45+T07
iFztFZK7v/uc8jdkCQkGEArs80nqaj2+0y5QaMtbZu6ewSd3RJ7eMJZStodxO8ES
YqpSXqOckiDcqkY38sNHi6S2CVwlKilzIbzZTa4Yx0eJF5VrjDlfjU9qQ0XlUTc/
Xqv7JfZZmBIAC2MzlgE=
-----END PRIVATE KEY-----"""


# pylint: disable=redefined-outer-name,protected-access


@pytest.fixture
def jwt_provider():
    """Returns a JwtAuthProvider instance with a mocked private key file."""
    with patch("builtins.open", mock_open(read_data=MOCK_PRIVATE_KEY)):
        provider = JwtAuthProvider(
            project_id="test-project",
            private_key_file="fake/path/key.pem",
            algorithm="RS256"
        )
    return provider


def test_jwt_provider_initial_state(jwt_provider):
    """Test that the provider needs a refresh immediately upon creation."""
    assert jwt_provider.needs_refresh() is True


def test_jwt_provider_token_caching(jwt_provider):
    """Test that a token is generated and then cached."""
    with freeze_time("2025-10-17 12:00:00"):
        # First call generates a token
        token1 = jwt_provider.get_password()
        assert token1 is not None
        assert jwt_provider.needs_refresh() is False  # Should be cached

        # Second call should return the exact same cached token
        token2 = jwt_provider.get_password()
        assert token1 == token2


@freeze_time("2025-10-17 12:00:00")
def test_jwt_provider_needs_refresh_logic():
    """Test the time-based refresh logic."""
    with patch("builtins.open", mock_open(read_data=MOCK_PRIVATE_KEY)):
        jwt_provider = JwtAuthProvider(
            project_id="test-project",
            private_key_file="fake/path/key.pem",
            algorithm="RS256",
            token_config=JwtTokenConfig(lifetime_minutes=60)
        )

    # 1. Get initial token
    token1 = jwt_provider.get_password()
    assert jwt_provider.needs_refresh() is False

    # 2. Move time forward, but not past the refresh buffer
    with freeze_time("2025-10-17 12:54:00"):
        assert jwt_provider.needs_refresh() is False
        assert jwt_provider.get_password() == token1  # Still cached

    # 3. Move time past the refresh buffer
    with freeze_time("2025-10-17 12:55:01"):
        assert jwt_provider.needs_refresh() is True
        token2 = jwt_provider.get_password()
        assert token2 is not None
        assert token1 != token2  # New token generated
        assert jwt_provider.needs_refresh() is False  # Cached again


def test_init_raises_file_not_found():
    """
    Tests that the constructor correctly raises FileNotFoundError
    if the private key file is missing.
    """
    with patch("builtins.open") as mock_open_fn:
        mock_open_fn.side_effect = FileNotFoundError("File not found")
        with pytest.raises(FileNotFoundError):
            JwtAuthProvider(
                project_id="test-project",
                private_key_file="fake/path/key.pem",
                algorithm="RS256"
            )


def test_jwt_encoding_failure(jwt_provider, caplog):
    """
    Tests that if jwt.encode() fails, it logs an error and
    returns a safe empty string.
    """
    with patch("src.udmi.core.auth.jwt_auth_provider.jwt.encode",
               side_effect=PyJWTError("Mock encode error")):
        password = jwt_provider.get_password()

    assert "Failed to generate new JWT: Mock encode error" in caplog.text
    assert "No valid JWT token available to return" in caplog.text
    assert password == ""


def test_default_token_lifetime_is_60_minutes():
    """
    Tests that the default token_lifetime_minutes is 60.
    """
    with patch("builtins.open", mock_open(read_data=MOCK_PRIVATE_KEY)):
        with freeze_time("2025-10-17 12:00:00") as ft:
            provider = JwtAuthProvider(
                project_id="test-project",
                private_key_file="fake/path/key.pem",
                algorithm="RS256"
            )
            provider.get_password()
            assert provider.needs_refresh() is False  # Should be cached

            ft.move_to("2025-10-17 12:55:01")
            assert provider.needs_refresh() is True
