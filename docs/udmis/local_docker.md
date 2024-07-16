[**UDMI**](../../) / [**Docs**](../) / [UDMIS](.) / [Local Docker](#)

# Local docker UDMIS setup and execution

* Identify a site model and parameters
  * For default reference udmi site model:
    * `bin/clone_model`
    * `site_model=sites/udmi_site_model`
    * `device_id=AHU-1`
    * `serial_no=21874812`
* docker run `bin/docker_udmis ${site_model}`
* db files `ls -l var/`
* registrar `bin/registrar ${site_model} //mqtt/localhost`
* pubber `bin/pubber ${site_model} //mqtt/localhost ${device_id}`

# Container build

General notes on how to build/push the upstream docker image.

* `bin/container udmis push`
* `docker tag udmis:latest ghcr.io/faucetsdn/udmi:latest`
* `docker push ghcr.io/faucetsdn/udmi:latest`
