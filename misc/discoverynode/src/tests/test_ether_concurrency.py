"""Two concurrent EtherDiscovery nmap scans must not share an output file."""

import os
import threading
from unittest import mock

import udmi.discovery.ether
import udmi.schema.state as state
from udmi.schema.config import DiscoveryFamily


class _FakeProc:
  """Minimal stand-in for subprocess.Popen used as a context manager."""

  def __init__(self, *args, **kwargs):
    self.cmd = args[0]
    self.stdout = mock.MagicMock()
    self.stdout.read.return_value = b""
    self.stdout.fileno.return_value = 0

  def __enter__(self):
    return self

  def __exit__(self, *exc):
    return False

  def poll(self):
    return 0


def _make(scan_id):
  ether = udmi.discovery.ether.EtherDiscovery(state.Discovery(), mock.MagicMock(), scan_id=scan_id)
  ether.config = DiscoveryFamily(generation="2026-01-01T00:00:00Z", depth="ports", addrs=["127.0.0.1"])
  ether.generation = None
  ether.count_events = 0
  return ether


def test_concurrent_nmap_scans_use_distinct_output_files():
  procs = []
  parsed = []

  def fake_popen(*args, **kwargs):
    p = _FakeProc(*args, **kwargs)
    procs.append(p)
    return p

  def fake_reader(path):
    parsed.append((path, os.path.exists(path)))
    return iter(())

  a, b = _make("scans/hourly_ping"), _make("scans/nightly_ports")
  with mock.patch("subprocess.Popen", side_effect=fake_popen), \
       mock.patch("os.set_blocking"), \
       mock.patch("udmi.discovery.utils.nmap.results_reader", side_effect=fake_reader):
    threads = [threading.Thread(target=a.nmap_runner), threading.Thread(target=b.nmap_runner)]
    for t in threads:
      t.start()
    for t in threads:
      t.join(10)

  outputs = [p.cmd[p.cmd.index("-oX") + 1] for p in procs]
  assert len(outputs) == 2
  assert outputs[0] != outputs[1]
  assert any("scans_hourly_ping" in o for o in outputs)
  assert any("scans_nightly_ports" in o for o in outputs)
  # file existed while parsing, and was removed afterwards
  assert all(existed for _, existed in parsed)
  assert not any(os.path.exists(o) for o in outputs)
