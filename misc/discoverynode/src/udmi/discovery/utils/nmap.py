"""Utils for NMAP Scans"""

import dataclasses
from typing import Generator
import xml.etree.ElementTree

@dataclasses.dataclass(unsafe_hash=True, order=True)
class NmapPort:
  """NMAP port details from result

  Attributes: service   name of service running on port port    port number
  state     state of port according to NMAP protocol  tcp/udp product   product
  name according to NMAP tunnel    if service is tunneled through SSL open
  (bool) if port is considered open
  """

  port_number: int
  sort_index: int = dataclasses.field(init=False, repr=False)
  service: str | None = None
  version: str | None = None 
  state: str | None = None
  protocol: str | None = None
  product: str | None = None
  banner: str | None = None

  def __post_init__(self):
    self.sort_index = int(self.port_number)


@dataclasses.dataclass(unsafe_hash=True, order=True)
class NmapHost:
  ip: str | None = None
  hostname: str | None = None
  ports: list[NmapPort] = dataclasses.field(default_factory=list)


def results_reader(nmap_file: str) -> Generator[NmapHost, None, None]:
  """Load NMAP Results Reader

  Args: nmap_file  path to NMAP file output (generated with -xO argument)

  Yields
    NmapHost resu
  """
  hosts = []
  root = xml.etree.ElementTree.parse(nmap_file).getroot()
  for _host in root.iter('host'):
    host = NmapHost()

    if (hostname := _host.find('hostname')) is not None:
      host.hostname = hostname.attrib['name']

    if (ipaddr := _host.find('address')) is not None:
      host.ip = ipaddr.attrib['addr']

    for _port in _host.iter('port'):
      port = NmapPort(port_number=_port.attrib['portid'])
      port.protocol = _port.attrib['protocol']
      port.state = _port.find('state').attrib['state']

      if (script := _port.find('script')) is not None and script.attrib[
          'id'
      ] == 'banner':
        port.banner = script.attrib['output']

      if (service := _port.find('service')) is not None:
        port.product = service.attrib.get('product')
        if service.attrib['method'] == 'probed':
          port.service = service.attrib['name']
          port.version = service.attrib.get('version')

      host.ports.append(port)

    yield host
