import pytest
import discovery_nmap
import os
from unittest import mock

def test_nmap_result_reader():
  file_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "nmaplocalhost.xml")
  mock_state = mock.MagicMock()
  mock_publisher = mock.MagicMock()
  nmap = discovery_nmap.NmapBannerScan(mock_state, mock_publisher)
  results = list(nmap.results_reader(file_path))

  expected_result = [
    discovery_nmap.NmapHost(ip="127.0.0.1", hostname=None, ports=[discovery_nmap.NmapPort(port_number="22", state="open", protocol="tcp", banner="SSH-2.0-OpenSSH_9.8 metadata-inspection GoogleMDI-4.0 AAAAIKIF9\nXFUfDT3Dr8hNqEsh2En+9VlXf+ZyelLOm/S1P4sAAAAIO4WSRvQGw0fbyDOuemkd4xd+...\n")])
  ]
  assert expected_result == results