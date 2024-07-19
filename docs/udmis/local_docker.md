[**UDMI**](../../) / [**Docs**](../) / [UDMIS](.) / [Local Docker](#)

# Local docker UDMIS setup and execution

## Configuration Setup

```
site_model=udmi_site_model
device_id=AHU-1
serial_no=8127324
```

## Environment Setup

```
docker inspect -f ok udminet || docker network create udminet --subnet 192.168.99.0/24
[[ -d ${site_model} ]] || git clone https://github.com/faucetsdn/${site_model}.git
```

## UDMIS Container Startup

```
docker run -d --rm --net udminet --name udmis -p 8883:8883 \
    -v $(realpath $site_model):/root/site \
    -v $PWD/var/tmp:/tmp \
    -v $PWD/var/etcd:/root/udmi/default.etcd \
    -v $PWD/var/mosquitto:/etc/mosquitto \
    ghcr.io/faucetsdn/udmi:udmis-latest udmi/bin/start_local block site/ //mqtt/udmis
```

## UDMIS Startup Diagnostics

[sample UDMIS output](udmis_output.md)

```
docker logs udmis 2>&1 | fgrep udmis
fgrep pod_ready var/tmp/udmis.log
ls -l var/tmp/pod_ready.txt
```

## Registrar Run

[sample registrar output](registrar_output.md)

```
docker run --rm --net udminet --name registrar -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/registrar site/ //mqtt/udmis
```

## Pubber Instance

[sample pubber output](pubber_output.md)

```
docker run --rm --net udminet --name pubber -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:pubber-latest bin/pubber site/ //mqtt/udmis ${device_id} ${serial_no}
```

## Sequencer Tests

[sample sequencer output](sequencer_output.md)

```
docker run --rm --net udminet --name sequencer -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/sequencer site/ //mqtt/udmis ${device_id} ${serial_no}
```

# Container Build

From within a working udmi install, the following can be used to build/push all requisite docker images:

```
for image in udmis validator pubber; do
  bin/container $image push \
  && docker tag $image:latest ghcr.io/faucetsdn/udmi:$image-latest \
  && docker push ghcr.io/faucetsdn/udmi:$image-latest
done
```
