import pytest
import udmi.discovery.utils.nmap
import os
from unittest import mock

def test_nmap_result_reader():
  file_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "nmaplocalhost.xml")
  results = list(udmi.discovery.utils.nmap.results_reader(file_path))

  expected_result = [
    udmi.discovery.utils.nmap.NmapHost(ip="127.0.0.1", hostname=None, os=None, ports=[udmi.discovery.utils.nmap.NmapPort(port_number="22", state="open", protocol="tcp", banner="SSH-2.0-OpenSSH_9.8 metadata-inspection GoogleMDI-4.0 AAAAIKIF9\nXFUfDT3Dr8hNqEsh2En+9VlXf+ZyelLOm/S1P4sAAAAIO4WSRvQGw0fbyDOuemkd4xd+...\n")])
  ]
  assert expected_result == results


def test_nmap_result_reader_with_os(tmp_path):
  xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<nmaprun scanner="nmap">
  <host>
    <address addr="192.168.1.10" addrtype="ipv4"/>
    <os>
      <osmatch name="Linux 5.4" accuracy="100"/>
      <osmatch name="Linux 4.15" accuracy="90"/>
    </os>
    <ports>
      <port protocol="tcp" portid="80">
        <state state="open"/>
      </port>
    </ports>
  </host>
</nmaprun>"""
  xml_file = tmp_path / "nmap_os.xml"
  xml_file.write_text(xml_content)
  results = list(udmi.discovery.utils.nmap.results_reader(str(xml_file)))
  assert len(results) == 1
  assert results[0].ip == "192.168.1.10"
  assert results[0].os == "Linux 5.4"


def test_nmap_result_reader_partial_xml(tmp_path):
  xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE nmaprun>
<nmaprun scanner="nmap" start="1723556283" version="7.94SVN" xmloutputversion="1.05">
  <scaninfo type="syn" protocol="tcp" numservices="65535" services="1-65535"/>
  <hosthint><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.1.10" addrtype="ipv4"/>
    <hostnames/>
  </hosthint>
  <host starttime="1723556283" endtime="1723556300">
    <status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.1.10" addrtype="ipv4"/>
    <ports>
      <port protocol="tcp" portid="22">
        <state state="open" reason="syn-ack" reason_ttl="64"/>
        <service name="ssh" product="OpenSSH" version="9.8" method="probed" conf="10"/>
      </port>
    </ports>
  </host>
"""
  xml_file = tmp_path / "nmap_partial.xml"
  xml_file.write_text(xml_content)
  results = list(udmi.discovery.utils.nmap.results_reader(str(xml_file)))
  assert len(results) == 1
  assert results[0].ip == "192.168.1.10"
  assert len(results[0].ports) == 1
  assert results[0].ports[0].port_number == "22"
  assert results[0].ports[0].service == "ssh"


def test_nmap_result_reader_incremental_offset(tmp_path):
  xml_file = tmp_path / "nmap_stream.xml"
  chunk1 = (
      '<?xml version="1.0" encoding="UTF-8"?>\n'
      '<nmaprun scanner="nmap">\n'
      '  <host starttime="1" endtime="2">\n'
      '    <address addr="10.0.0.1" addrtype="ipv4"/>\n'
      '    <ports><port protocol="tcp" portid="22"><state state="open"/></port></ports>\n'
      '  </host>\n'
      '  <host starttime="3" endtime="4">\n'
      '    <address addr="10.0.0.2" addrtype="ipv4"/>\n'
  )
  chunk2 = (
      '    <ports><port protocol="tcp" portid="80"><state state="open"/></port></ports>\n'
      '  </host>\n'
      '</nmaprun>\n'
  )
  xml_file.write_text(chunk1)

  reader = udmi.discovery.utils.nmap.results_reader(str(xml_file))
  first_batch = list(reader)
  assert [h.ip for h in first_batch] == ["10.0.0.1"]
  assert reader.offset == len(chunk1.encode("utf-8"))

  # Reading again with no new data yields nothing and does not advance offset
  assert list(reader) == []
  assert reader.offset == len(chunk1.encode("utf-8"))

  # Appending the remainder of the second host parses only the newly appended bytes
  with open(xml_file, "a", encoding="utf-8") as f:
    f.write(chunk2)

  second_batch = list(reader)
  assert [h.ip for h in second_batch] == ["10.0.0.2"]
  assert reader.offset == len((chunk1 + chunk2).encode("utf-8"))


