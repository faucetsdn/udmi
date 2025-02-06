import logging
import os
import subprocess
import threading
from typing import Callable

from udmi_schema.schema.events_discovery import DiscoveryEvents
from udmi_schema.schema.state import State

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager
from udmi.client.manager.discovery.discovery_manager import (
    catch_exceptions_to_status,
    mark_task_complete_on_return
)
from udmi.util import nmap


class NmapBannerScan(DiscoveryManager):

    scan_family = "ether"

    def __init__(self,
                 state: State,
                 publisher: Callable[[DiscoveryEvents], None],
                 *,
                 target_ips: list[str]):
        self.cancel_threads = threading.Event()
        self.target_ips = target_ips
        self.runner_thread = None
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        self.cancel_threads.clear()
        self.runner_thread = threading.Thread(
            target=self.runner, args=[], daemon=True
        )
        self.runner_thread.start()

    def stop_discovery(self) -> None:
        logging.info(f"stopping scan {self.__class__.__name__}")
        self.cancel_threads.set()
        self.runner_thread.join()

    @mark_task_complete_on_return
    @catch_exceptions_to_status
    def runner(self):
        nmap_output_file = "nmap_localhost.xml"

        with subprocess.Popen(
            [
                "/usr/bin/nmap",
                "--script",
                "banner",
                "127.0.0.1",
                "-oX",
                nmap_output_file,
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
                        logging.error("terminating nmap because stop signal "
                                      "received")
                        proc.terminate()
                        return

        logging.info("nmap scan complete, parsing results")

        for host in nmap.results_reader(nmap_output_file):
            event = DiscoveryEvents(
                generation=self.generation,
                scan_family=self.scan_family,
                scan_addr=host.ip,
                families={
                    "port": {p.port_number: {"banner": p.banner} for p in
                             host.ports}
                },
            )
            self.publish(event.to_json())
