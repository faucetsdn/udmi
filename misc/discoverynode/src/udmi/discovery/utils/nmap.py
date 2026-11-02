"""Utils for NMAP Scans"""

import dataclasses
import os
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
  os: str | None = None
  ports: list[NmapPort] = dataclasses.field(default_factory=list)


class results_reader:
  """Incremental NMAP XML Results Reader.

  Maintains a byte offset and XMLPullParser across iterations so each iteration
  reads and parses only bytes appended to `nmap_file` since the previous read.
  """

  def __init__(self, nmap_file: str):
    self.nmap_file = nmap_file
    self.offset = 0
    self._parser = xml.etree.ElementTree.XMLPullParser(events=('start', 'end'))
    self._root = None

  def __iter__(self) -> Generator[NmapHost, None, None]:
    try:
      current_size = os.path.getsize(self.nmap_file)
    except OSError:
      return
    if current_size <= self.offset:
      return
    with open(self.nmap_file, 'rb') as f:
      f.seek(self.offset)
      while chunk := f.read(65536):
        self.offset += len(chunk)
        self._parser.feed(chunk)
        for event_type, elem in self._parser.read_events():
          if event_type == 'start':
            if elem.tag == 'nmaprun':
              self._root = elem
            continue
          if elem.tag != 'host':
            continue
          host = NmapHost()

          if (hostname := elem.find('hostname')) is not None:
            host.hostname = hostname.attrib['name']

          if (ipaddr := elem.find('address')) is not None:
            host.ip = ipaddr.attrib['addr']

          if (osmatch := elem.find('os/osmatch')) is not None:
            host.os = osmatch.attrib.get('name')

          for _port in elem.iter('port'):
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
          elem.clear()
          if self._root is not None:
            self._root.clear()


