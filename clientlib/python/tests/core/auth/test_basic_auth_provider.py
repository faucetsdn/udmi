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
