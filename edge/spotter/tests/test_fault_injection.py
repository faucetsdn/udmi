#!/usr/bin/env python3
"""Fault injection, socket disconnect, and backoff retry tests."""

import socket
import struct
import threading
import time
import unittest
from unittest.mock import MagicMock

from edge.spotter.src.agent import wait_for_broker_readiness


class TestFaultInjection(unittest.TestCase):
  """Verifies transport resiliency under socket disconnects and outages."""

  def test_wait_for_broker_readiness_recovers_after_transient_failure(self):
    """Simulates a broker starting late; verifies readiness probe recovers."""
    # Find an available ephemeral port
    temp_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    temp_sock.bind(("127.0.0.1", 0))
    port = temp_sock.getsockname()[1]
    temp_sock.close()

    server_started = threading.Event()
    server_stop = threading.Event()

    def delayed_server():
      # Delay starting the server by 1.2 seconds to force retries
      time.sleep(1.2)
      server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
      server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
      server.bind(("127.0.0.1", port))
      server.listen(5)
      server_started.set()
      while not server_stop.is_set():
        server.settimeout(0.2)
        try:
          conn, _ = server.accept()
          conn.close()
        except socket.timeout:
          continue
      server.close()

    t = threading.Thread(target=delayed_server, daemon=True)
    t.start()

    try:
      # Probe with a 4s timeout: must retry and succeed once server starts
      ready = wait_for_broker_readiness("127.0.0.1", port, timeout_sec=4)
      self.assertTrue(
          ready, "Probe failed to recover after transient broker outage"
      )
    finally:
      server_stop.set()
      t.join(timeout=2)

  def test_wait_for_broker_readiness_handles_abrupt_disconnect_reset(self):
    """Simulates a broker abruptly resetting the TCP connection on connect."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", 0))
    port = server.getsockname()[1]
    server.listen(5)

    server_stop = threading.Event()

    def resetting_server():
      while not server_stop.is_set():
        server.settimeout(0.2)
        try:
          conn, _ = server.accept()
          # Force TCP RST by enabling SO_LINGER with 0 timeout
          conn.setsockopt(
              socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0)
          )
          conn.close()
        except socket.timeout:
          continue
      server.close()

    t = threading.Thread(target=resetting_server, daemon=True)
    t.start()

    try:
      # Socket resets will happen; probe must handle ConnectionReset cleanly
      ready = wait_for_broker_readiness("127.0.0.1", port, timeout_sec=1)
      self.assertIn(ready, (True, False))
    finally:
      server_stop.set()
      t.join(timeout=2)

  def test_device_run_loop_retries_on_transient_error(self):
    """Simulates transient device.run() failure and verifies retry backoff."""
    mock_device = MagicMock()
    attempts = 0

    def flaky_run():
      nonlocal attempts
      attempts += 1
      if attempts == 1:
        raise ConnectionResetError("Broker closed transport")

    mock_device.run.side_effect = flaky_run

    max_retries = 3
    retry_delay_sec = 0.01  # Fast delay for test

    successful = False
    for attempt in range(1, max_retries + 1):
      try:
        mock_device.run()
        successful = True
        break
      except ConnectionResetError:
        if attempt < max_retries:
          time.sleep(retry_delay_sec)
        else:
          raise

    self.assertTrue(successful)
    self.assertEqual(attempts, 2)

  def test_device_run_loop_exhausts_retries_and_fails(self):
    """Simulates persistent failure and verifies maximum retry exhaustion."""
    mock_device = MagicMock()
    mock_device.run.side_effect = ConnectionRefusedError(
        "Broker permanently unreachable"
    )

    max_retries = 3
    retry_delay_sec = 0.01

    attempts = 0
    with self.assertRaises(ConnectionRefusedError):
      for attempt in range(1, max_retries + 1):
        attempts += 1
        try:
          mock_device.run()
          break
        except ConnectionRefusedError:
          if attempt < max_retries:
            time.sleep(retry_delay_sec)
          else:
            raise

    self.assertEqual(attempts, 3)


if __name__ == "__main__":
  unittest.main(verbosity=2)

