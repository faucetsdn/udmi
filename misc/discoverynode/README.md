# Discovery

## UDMI MQTT Client

`sudo ~/venv/bin/python3 main.py`

## Unit Tests
`~/venv/bin/python3 -m pytest tests/`

## Integration test (no MQTT)

```
sudo testing/integration/integration_test.sh

sudo docker ps -a | grep "discoverynode-test-" | awk '{print $1}' | sudo xargs docker stop

docker run --network=test-network -v  $PWD/dump:/data --rm -ti travelping/pcap

```

