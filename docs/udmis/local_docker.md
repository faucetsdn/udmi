[**UDMI**](../../) / [**Docs**](../) / [UDMIS](.) / [Local Docker](#)

# Local docker UDMIS setup and execution

```
docker inspect -f ok udminet || docker network create udminet
set -u
[[ -d udmi_site_model ]] || git clone https://github.com/faucetsdn/udmi_site_model.git
site_model=$PWD/udmi_site_model
serial_no=8127324
device_id=AHU-1
echo Ready for site ${site_model} device ${device_id} serial ${serial_no}
```

```
docker run -d --rm --net udminet --name udmis -p 8883:8883 \
    -v ${site_model}:/root/site \
    -v $PWD/var/tmp:/tmp \
    -v $PWD/var/etcd:/root/udmi/default.etcd \
    -v $PWD/var/mosquitto:/etc/mosquitto \
    ghcr.io/faucetsdn/udmi:udmis-latest udmi/bin/start_local block site/ //mqtt/localhost
```

```
docker logs udmis 2>&1 | fgrep udmis
docker exec udmis fgrep pod_ready udmi/out/udmis.log
ls -l var/tmp/pod_ready.txt
```

[sample diagnostic output](udmis_output.md)


```
docker run --rm --net udminet --name validator \
    -v ${site_model}:/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/registrar site/ //mqtt/udmis
```

[sample registrar output](registrar_output.md)

```
docker run --rm --net udminet --name pubber \
    -v ${site_model}:/root/site \
    ghcr.io/faucetsdn/udmi:pubber-latest bin/pubber site/ //mqtt/udmis AHU-1 ${serial_no}
```

[sample pubber_output](pubber_output.md)

# Container Build

From within a working udmi install, the following can be used to build/push all requisite docker images:

```
for image in udmis validator pubber; do
  bin/container $image push
  docker tag $image:latest ghcr.io/faucetsdn/udmi:$image-latest
  docker push ghcr.io/faucetsdn/udmi:$image-latest
done
```
