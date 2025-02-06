"""
Utils for NMAP Scans.

This module provides data structures and functions for parsing and representing
Nmap scan results from XML output.
"""

import dataclasses
import xml.etree.ElementTree
from typing import Generator


@dataclasses.dataclass(unsafe_hash=True, order=True)
class NmapPort:
    """
    Represents a single port found during an NMAP scan.

    Attributes:
        port_number: The port number (integer).
        sort_index: Used for sorting ports (automatically set to port_number).
        service: The service name running on the port (string, optional).
        state: The state of the port (e.g, "open", "closed") (string, optional).
        protocol: The protocol used (e.g, "tcp", "udp") (string, optional).
        product: The product name identified on the port (string, optional).
        banner: The banner captured from the port (string, optional).
    """

    port_number: int
    sort_index: int = dataclasses.field(init=False, repr=False)
    service: str | None = None
    state: str | None = None
    protocol: str | None = None
    product: str | None = None
    banner: str | None = None

    def __post_init__(self):
        """
        Initializes the sort_index to the port number for easy sorting.
        """
        self.sort_index = int(self.port_number)


@dataclasses.dataclass(unsafe_hash=True, order=True)
class NmapHost:
    """
    Represents a host scanned by NMAP.

    Attributes:
        ip: The IP address of the host (string, optional).
        hostname: The hostname of the host (string, optional).
        ports: A list of NmapPort objects representing the open ports on the
               host.
    """

    ip: str | None = None
    hostname: str | None = None
    ports: list[NmapPort] = dataclasses.field(default_factory=list)


def results_reader(nmap_file: str) -> Generator[NmapHost, None, None]:
    """
    Parses an NMAP XML file and yields NmapHost objects.

    Args:
        nmap_file: The path to the Nmap XML file (generated with -oX argument).

    Yields:
        NmapHost: An NmapHost object representing a host scanned by Nmap.
    """
    try:
        tree = xml.etree.ElementTree.parse(nmap_file)
        root = tree.getroot()
    except FileNotFoundError:
        print(f"Error: File not found: {nmap_file}")
        return
    except xml.etree.ElementTree.ParseError as e:
        print(f"Error: XML parsing error in {nmap_file}: {e}")
        return

    for host_element in root.iter('host'):
        host = NmapHost()

        hostname_element = host_element.find('hostname')
        if hostname_element is not None:
            host.hostname = hostname_element.attrib.get('name')

        ipaddr_element = host_element.find('address')
        if ipaddr_element is not None:
            host.ip = ipaddr_element.attrib.get('addr')

        for port_element in host_element.iter('port'):
            try:
                port = NmapPort(port_number=int(port_element.attrib['portid']))
                port.protocol = port_element.attrib['protocol']

                state_element = port_element.find('state')
                if state_element is not None:
                    port.state = state_element.attrib['state']

                script_element = port_element.find('script')
                if script_element is not None and \
                   script_element.attrib.get('id') == 'banner':
                    port.banner = script_element.attrib.get('output')

                service_element = port_element.find('service')
                if service_element is not None:
                    port.product = service_element.attrib.get('product')
                    if service_element.attrib.get('method') == 'probed':
                        port.service = service_element.attrib.get('name')

                host.ports.append(port)

            except (KeyError, ValueError) as e:
                print(
                    f"Warning: Error parsing port data: {e}. Skipping port.")
                continue

        yield host
