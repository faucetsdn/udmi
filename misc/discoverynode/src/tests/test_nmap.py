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
