[**UDMI**](../../) / [**Docs**](../) / [UDMIS](.) / [Local Docker](#)

# Local docker UDMIS setup and execution

* `docker network create udminet`
* `newgrp docker`

```
site_model=$PWD/sites/udmi_site_model
```

```
docker run -d --rm --net udminet --name udmis -p 8883:8883 \
    -v $site_model:/root/site \
    -v $PWD/var/etcd:/root/default.etcd \
    -v $PWD/var/mosquitto:/etc/mosquitto \
    ghcr.io/faucetsdn/udmi:udmis-latest udmi/bin/start_local block site/ //mqtt/localhost
```

```
docker run --rm --net udminet --name validator \
    -v $site_model:/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/registrar site/ //mqtt/udmis
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

# Container build

General notes on how to build/push the upstream docker image.

```
for image in udmis validator pubber; do
  bin/container $image push
  docker tag $image:latest ghcr.io/faucetsdn/udmi:$image-latest
  docker push ghcr.io/faucetsdn/udmi:$image-latest
done
```
