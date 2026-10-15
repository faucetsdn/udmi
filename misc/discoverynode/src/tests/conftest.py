"""Shared pytest fixtures for discoverynode tests."""

from unittest import mock

import pytest
import udmi.discovery.discovery as discovery


@pytest.fixture(autouse=True)
def _shutdown_controllers():
  """Stops every DiscoveryController created during a test.

  Controllers own scheduler/worker threads; without this, scans started by
  one test keep running (and logging, and hitting class-level mocks) in
  later tests.
  """
  created = []
  original_init = discovery.DiscoveryController.__init__

  def tracking_init(self, *args, **kwargs):
    original_init(self, *args, **kwargs)
    created.append(self)

  with mock.patch.object(discovery.DiscoveryController, "__init__", tracking_init):
    yield

  for controller in created:
    try:
      controller.shutdown()
    except Exception:  # pylint: disable=broad-except
      pass
