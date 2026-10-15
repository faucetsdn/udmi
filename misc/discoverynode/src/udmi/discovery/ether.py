import collections
import concurrent.futures
import dataclasses
import ipaddress
import logging
import os
import shlex
import signal
import subprocess
import tempfile
import threading
import udmi.discovery.discovery as discovery
import udmi.discovery.utils.nmap as nmap
import udmi.schema.discovery_event
import udmi.schema.state

from typing import Iterable


def future_wait_and_count_outstanding(futures: Iterable[concurrent.futures.Future], timeout: int) -> int:
  """A wrapper arround concurrent.futures.wait which returns a count of 
  outstanding (not done or cancelled) futures.

  Args:
    futures: iteratable of futures
    timeout: maximum time to wait
  
  Returns:
    count of outstanding futures
  """
  _, outstanding = concurrent.futures.wait(futures, timeout)
  return len(outstanding)


class EtherDiscovery(discovery.DiscoveryController):
  """Ether Network Discovery."""

  family = "ether"

  def __init__(self, state_entry, publisher, *, scan_id, ping_concurrency: int | None = None):
    self.cancel_threads = threading.Event()
    self.nmap_thread = None
    self.ping_thread = None
    self.last_bathometer_reading = None
    self.ping_concurrency = ping_concurrency or 4
    super().__init__(state_entry, publisher, scan_id=scan_id)

  def start_discovery(self) -> None:
    prefix = self.magic_bathometer(self.config.depth)
    self.log.info(f"magic bathometer read {prefix}")
    self.last_bathometer_reading = prefix
    getattr(self, f"{prefix}_start_discovery")()

  def stop_discovery(self) -> None:
    if not self.last_bathometer_reading:
      self.log.info("not stopping because no known bathometer reading")
      return

    self.log.info("calling stop for %s", self.last_bathometer_reading)
    getattr(self, f"{self.last_bathometer_reading}_stop_discovery")()

  def magic_bathometer(self, depth: str) -> str:
    """Identifies the discovery scan to run based on the given depth"""
    match depth:
      case "ping":
        return "ping"
      case "ports" | "services":
        return "nmap"
      case _:
        raise RuntimeError(f"unmatched depth {depth}")

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def ping_dispatcher(self, target_ips: list[str]):
    """Dispatches ping tasks across simulateous workers"""
    if not target_ips:
      self.log.warning("no targets given for ping scan")
      return
    hosts_iter = (
        str(host)
        for target in target_ips
        for host in ipaddress.ip_network(target, strict=False).hosts()
    )
    max_in_flight = max(self.ping_concurrency * 2, 1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=self.ping_concurrency) as executor:
      in_flight: set[concurrent.futures.Future] = set()
      exhausted = False
      while not exhausted or in_flight:
        if self.cancel_threads.is_set():
          executor.shutdown(True, cancel_futures=True)
          break
        while not exhausted and len(in_flight) < max_in_flight:
          if self.cancel_threads.is_set():
            break
          try:
            ip = next(hosts_iter)
          except StopIteration:
            exhausted = True
            break
          in_flight.add(executor.submit(self.ping_task, ip))
        if not in_flight:
          break
        return_when = (
            concurrent.futures.ALL_COMPLETED
            if exhausted
            else concurrent.futures.FIRST_COMPLETED
        )
        _, in_flight = concurrent.futures.wait(
            in_flight, timeout=1, return_when=return_when
        )

  def ping_task(self, target_ip: str) -> bool:
    """Sends a single ping to the target IP. Returns True if successful."""
    self.log.debug("ping task %s", target_ip)
    try:
      # -c 1: expect 1 packet
      # -w 2: wait 2 seconds for a response
      result = subprocess.run(
          ["/usr/bin/ping", "-c", "1", "-W", "2", target_ip],
          stdout=subprocess.PIPE,
          stderr=subprocess.STDOUT,
          encoding="utf-8",
          check=True,
          timeout=5,
      )
      event = udmi.schema.discovery_event.DiscoveryEvent(
          generation=self.generation,
          family=self.family,
          # hacky because the mac address is unknown to the ping
          addr=None,
          families=dict(
                  ipv4=udmi.schema.discovery_event.DiscoveryFamily(
                      target_ip
                  )
          )
      )
      self.publish(event)

    except subprocess.CalledProcessError as e:
      self.log.debug(f"Ping failed for %s: %s", target_ip, e.output)
      return False
    except Exception as e:
      self.log.exception(f"Ping exception for %s", target_ip)
      return False

  def ping_start_discovery(self):
    """Start ping discovery scan."""
    self.cancel_threads.clear()
    self.ping_thread = threading.Thread(
        target=self.ping_dispatcher, args=[self.config.addrs], daemon=True
    )
    self.ping_thread.start()

  def ping_stop_discovery(self):
    """Stop ping discovery scan."""
    self.cancel_threads.set()
    if self.ping_thread and self.ping_thread.is_alive():
      self.ping_thread.join()

  def nmap_start_discovery(self):
    self.cancel_threads.clear()
    self.nmap_thread = threading.Thread(
        target=self.nmap_runner, args=[], daemon=True
    )
    self.nmap_thread.start()

  def nmap_stop_discovery(self):
    self.log.info("stopping")
    self.cancel_threads.set()
    if self.nmap_thread and self.nmap_thread.is_alive():
      self.nmap_thread.join()

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def nmap_runner(self):
    if not self.config.addrs:
      raise RuntimeError("no targets given for nmap scan")

    # Unique per scan instance so concurrent ether scans (e.g. ping + ports)
    # do not clobber each other's results.
    with tempfile.NamedTemporaryFile(
        prefix=f"nmap_{self.scan_id.replace('/', '_')}_", suffix=".xml", delete=False
    ) as f:
      output_file = f.name

    def _log_progress(raw_bytes: bytes) -> None:
      for line in raw_bytes.decode("utf-8", errors="replace").splitlines():
        if "Stats:" in line or "Timing:" in line:
          self.log.info("nmap: %s", line)

    try:
      cmd = ["/usr/bin/nmap"]
      if self.config.depth == "services":
        cmd.extend(["--script", "banner", "-A"])
      cmd.extend(["-p-", "-T3"])
      cmd.extend(self.config.addrs)
      cmd.extend(["-oX", output_file, "--stats-every", "60s"])

      # Binary pipe: a non-blocking read on a text-mode pipe returns None to the
      # decoder and raises TypeError when no output is pending.
      with subprocess.Popen(
          cmd,
          stdout=subprocess.PIPE,
          stderr=subprocess.STDOUT,
          preexec_fn=os.setsid,
      ) as proc:
        self.log.info("nmap scan started")
        os.set_blocking(proc.stdout.fileno(), False)
        buf = b""
        while True:
          if output := proc.stdout.read():
            buf += output
            *complete_lines, buf = buf.split(b"\n")
            if complete_lines:
              _log_progress(b"\n".join(complete_lines))
          if proc.poll() is not None:
            if output := proc.stdout.read():
              buf += output
            if buf:
              _log_progress(buf)
            break
          if self.cancel_threads.wait(timeout=1):
            self.log.error("terminating nmap because stop signal received")
            try:
              os.killpg(proc.pid, signal.SIGTERM)
            except (ProcessLookupError, PermissionError, OSError, TypeError):
              proc.terminate()
            try:
              proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
              proc.kill()
            return

      self.log.info("nmap scan complete, parsing results")

      for host in nmap.results_reader(output_file):
        event = udmi.schema.discovery_event.DiscoveryEvent(
            generation=self.generation,
            family=self.family,
            addr=host.ip,
            refs={
                f"{p.port_number}": {
                    "adjunct": {
                        k: v
                        for k, v in dataclasses.asdict(p).items()
                        if k != "sort_index"
                    }
                }
                for p in host.ports
            },
        )
        if host.os:
          event.system.ancillary["os"] = host.os
        self.publish(event)
    finally:
      if os.path.exists(output_file):
        os.unlink(output_file)
