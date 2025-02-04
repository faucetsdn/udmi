import logging
import threading

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager


class NmapBannerScan(DiscoveryManager):

    scan_family = "ether"

    def __init__(self, state, publisher, *, target_ips: list[str]):
        self.cancel_threads = threading.Event()
        self.target_ips = target_ips
        self.nmap_thread = None
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        self.cancel_threads.clear()
        self.nmap_thread = threading.Thread(
            target=self.nmap_runner, args=[], daemon=True
        )
        self.nmap_thread.start()

    def stop_discovery(self) -> None:
        logging.info(f"stopping scan {self.__class__.__name__}")
        self.cancel_threads.set()
        self.nmap_thread.join()

