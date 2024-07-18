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
    -v $PWD/var/etcd:/root/udmi/default.etcd \
    -v $PWD/var/mosquitto:/etc/mosquitto \
    ghcr.io/faucetsdn/udmi:udmis-latest udmi/bin/start_local block site/ //mqtt/localhost
```

```
docker exec udmis cat udmi/out/udmis.log
```


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

* Identify a site model and parameters
  * For default reference udmi site model:
    * `bin/clone_model`
    * `site_model=sites/udmi_site_model`
    * `device_id=AHU-1`
    * `serial_no=21874812`
* Run the standard docker image:
  * `bin/docker_udmis ${site_model}`
* Persistent DB files are mapped locally
  * `ls -l var/etcd var/mosquitto`
* Register your site with the server
  * `bin/registrar ${site_model} //mqtt/localhost`
* Optionally test with pubber instance
  * `sudo bin/keygen CERT ${site_model}/devices/${device_id}/`
  * `bin/pubber ${site_model} //mqtt/localhost ${device_id} ${serial_no}`

# Container Build

From within a working udmi install, the following can be used to build/push all requisite docker images:

```
for image in udmis validator pubber; do
  bin/container $image push
  docker tag $image:latest ghcr.io/faucetsdn/udmi:$image-latest
  docker push ghcr.io/faucetsdn/udmi:$image-latest
done
```
