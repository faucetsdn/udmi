"""
Authentication provider package for the UDMI device.

This package defines the core `AuthProvider` abstract base class
and provides concrete implementations for different auth strategies,
such as `BasicAuthProvider` (username/password) and `JwtAuthProvider` (JWT).

The imports in this file make these key classes directly available
from the `udmi.core.auth` namespace for easier use.
"""

from udmi.core.auth.auth_provider import AuthProvider
from udmi.core.auth.basic_auth_provider import BasicAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtAuthProvider
