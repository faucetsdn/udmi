"""
Unit tests for the `BasicAuthProvider` class.

This module verifies the functionality of the `BasicAuthProvider`, ensuring
it correctly stores and returns static credentials and reports that
it never needs to refresh.
"""

from src.udmi.core.auth import BasicAuthProvider


def test_getters():
    """
    test_getters
    Instantiate with "user", "pass".
    get_username() == "user" and get_password() == "pass".
    """
    username = "test_user"
    password = "test_password"

    provider = BasicAuthProvider(username=username, password=password)

    assert provider.get_username() == username
    assert provider.get_password() == password


def test_needs_refresh():
    """
    Assert needs_refresh() always returns False.
    """
    provider = BasicAuthProvider(username="user", password="pass")

    refresh_needed = provider.needs_refresh()

    assert refresh_needed is False


def test_repr_masks_password():
    """
    Verifies that the __repr__ method masks the password for security.
    This ensures credentials don't leak into logs if the object is printed.
    """
    provider = BasicAuthProvider(username="my_user", password="super_secret_password")

    repr_str = repr(provider)

    assert "my_user" in repr_str
    assert "super_secret_password" not in repr_str
    assert "***" in repr_str
