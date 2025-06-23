import collections
import concurrent.futures
import dataclasses
import logging
import os
import shlex
import subprocess
import threading
import udmi.discovery.discovery as discovery
import udmi.discovery.utils.nmap as nmap
import udmi.schema.discovery_event
import udmi.schema.state


class EtherDiscovery(discovery.DiscoveryController):
  """Passive Network Discovery."""

  family = "ether"

  def __init__(self, state, publisher, *, target_ips: list[str] = None):
    self.cancel_threads = threading.Event()
    self.target_ips = target_ips
    self.nmap_thread = None
    self.ping_thread = None
    self.last_bathometer_reading = None
    self.target_ips: collections.deque | None = None
    super().__init__(state, publisher)

  def start_discovery(self) -> None:
    prefix = self.magic_bathometer(self.config.depth)
    logging.info(f"magic bathometer read {prefix}")
    self.last_bathometer_reading = prefix
    getattr(self, f"{prefix}_start_discovery")()

  def stop_discovery(self) -> None:
    if not self.last_bathometer_reading:
      logging.info("not stopping because no known bathometer reading")
      return

    logging.info("calling stop for %s", self.last_bathometer_reading)
    getattr(self, f"{self.last_bathometer_reading}_stop_discovery")()

  def magic_bathometer(self, depth: str) -> str:
    """Identifies the discovery scan to run based on the given depth"""
    match depth:
      case "ping":
        return "ping"
      case "ports":
        return "nmap"
      case _:
        raise RuntimeError(f"unmatched depth {depth}")

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def ping_dispatcher(self, target_ips: list[str]):
    """Dispatches ping tasks across simulateous workers"""
    if not target_ips:
      logging.warning("no targets given for ping scan")
      return
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
      futures = [executor.submit(self.ping_task, ip) for ip in target_ips]
      while (
          concurrent.futures.wait(futures, 1)
          != concurrent.futures.ALL_COMPLETED
      ):

        if self.cancel_threads.is_set():
          executor.shutdown(True, cancel_futures=True)
          # cancelled futures return as not completed, so the loop must exit
          break

  def ping_task(self, target_ip: str) -> bool:
    """Sends a single ping to the target IP. Returns True if successful."""
    logging.debug("ping task %s", target_ip)
    try:
      # -c 1: expect 1 packet
      # -W 2: wait 2 seconds for a response
      result = subprocess.run(
          ["/usr/bin/ping", "-c", "1", "-2", "2", target_ip],
          stdout=subprocess.PIPE,
          stderr=subprocess.STDOUT,
          encoding="utf-8",
          check=True,
          timeout=5,
      )
      event = udmi.schema.discovery_event.DiscoveryEvent(
          generation=self.generation,
          family=self.family,
          addr=target_ip,
      )
      self.publish(event)

    except subprocess.CalledProcessError as e:
      logging.error(f"Ping failed for %s: %s", target_ip, e.output)
      return False
    except Exception as e:
      logging.error(f"Ping exception for %s: %s", target_ip, e)
      return False

  def ping_start_discovery(self):
    """Start ping discovery scan."""
    logging.info("starting ping discovery")
    self.cancel_threads.clear()
    self.ping_thread = threading.Thread(
        target=self.ping_dispatcher, args=[self.config.addrs], daemon=True
    )
    self.ping_thread.start()

  def ping_stop_discovery(self):
    """Stop ping discovery scan."""
    logging.info("stopping ping discovery")
    self.cancel_threads.set()
    if self.ping_thread and self.ping_thread.is_alive():
      self.ping_thread.join()

  def nmap_start_discovery(self):
    logging.error("hello its me")
    self.cancel_threads.clear()
    self.nmap_thread = threading.Thread(
        target=self.nmap_runner, args=[], daemon=True
    )
    self.nmap_thread.start()

  def nmap_stop_discovery(self):
    logging.info("stopping")
    self.cancel_threads.set()
    self.nmap_thread.join()

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def nmap_runner(self):
    OUTPUT_FILE = "nmaplocalhost.xml"

    with subprocess.Popen(
        [
            "/usr/bin/nmap",
            "--script",
            "banner",
            "-p-",
            "-T4",
            "-A",
            self.target_ips[0],
            "-oX",
            OUTPUT_FILE,
            "--stats-every",
            "5s",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        preexec_fn=os.setsid,
    ) as proc:
      logging.info("nmap scan started")
      while True:
        try:
          out, err = proc.communicate(timeout=1)
          break
        except subprocess.TimeoutExpired as e:
          if self.cancel_threads.is_set():
            logging.error("terminating nmap because stop signal received")
            proc.terminate()
            return

    logging.info("nmap scan complete, parsing results")

    for host in nmap.results_reader(OUTPUT_FILE):
      event = udmi.schema.discovery_event.DiscoveryEvent(
          generation=self.generation,
          family=self.family,
          addr=host.ip,
          refs={
              f"{p.port_number}": {"adjunct": dataclasses.asdict(p)}
              for p in host.ports
          },
      )
      self.publish(event)
