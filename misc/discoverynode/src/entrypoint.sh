echo hello from entry point
TIMESTAMP=$(date +%s)
echo $TIMESTAMP
tcpdump -ni eth0  -w /pcap/$TIMESTAMP.pcap &
python -m pytest -s tests/test_integration.py