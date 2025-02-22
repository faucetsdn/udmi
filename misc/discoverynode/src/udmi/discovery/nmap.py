import logging
import os
import shlex
import subprocess
import threading
import udmi.discovery.discovery as discovery
import udmi.discovery.utils.nmap as nmap
import udmi.schema.discovery_event
import udmi.schema.state
import dataclasses


class NmapBannerScan(discovery.DiscoveryController):
  """Passive Network Discovery."""

  family = "ether"

  def __init__(self, state, publisher, *, target_ips: list[str]):
    self.cancel_threads = threading.Event()
    self.target_ips = target_ips
    self.nmap_thread = None
    super().__init__(state, publisher)

  def start_discovery(self):
    self.cancel_threads.clear()
    self.nmap_thread = threading.Thread(
        target=self.nmap_runner, args=[], daemon=True
    )
    self.nmap_thread.start()

  def stop_discovery(self):
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
              f"{p.port_number}": {"aux": dataclasses.asdict(p)} for p in host.ports
          },
      )
      self.publish(event)
